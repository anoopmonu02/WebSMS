package com.smsweb.sms.services.smsmessage;

import com.smsweb.sms.dto.ClassRecipientCount;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.messaging.SmsConversation;
import com.smsweb.sms.models.messaging.SmsMessage;
import com.smsweb.sms.models.messaging.SmsMessageAttachment;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.universal.Grade;
import com.smsweb.sms.models.universal.Section;
import com.smsweb.sms.repositories.smsmessage.SmsMessageRepository;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.services.universal.GradeService;
import com.smsweb.sms.services.universal.SectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Class notification to several grade-section pairs in one go (Send Message > Notification > Class).
 *
 * Each selected grade x section that has at least one Active student gets its OWN message, built
 * exactly like the existing single-class send: one SmsMessage (grade + section set), one
 * conversation, one sms_message_recipients row per Active student, then a push to those students.
 * Pairs with no students are skipped (no empty message is created). An optional medium narrows
 * the students; no medium means all mediums, which is what a single-class send does today.
 *
 * Not @Transactional on purpose: every class is saved in its own transaction (as the existing
 * single-class send does), so one failing class cannot undo the classes already sent.
 * The existing endpoints and services are not changed by this class.
 */
@Service
public class ClassNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ClassNotificationService.class);
    private static final int MAX_IDS = 60;

    private final AcademicStudentRepository academicStudentRepository;
    private final SmsMessageRepository smsMessageRepository;
    private final SmsMessageService smsMessageService;
    private final GradeService gradeService;
    private final SectionService sectionService;

    public ClassNotificationService(AcademicStudentRepository academicStudentRepository,
                                    SmsMessageRepository smsMessageRepository,
                                    SmsMessageService smsMessageService,
                                    GradeService gradeService,
                                    SectionService sectionService) {
        this.academicStudentRepository = academicStudentRepository;
        this.smsMessageRepository = smsMessageRepository;
        this.smsMessageService = smsMessageService;
        this.gradeService = gradeService;
        this.sectionService = sectionService;
    }

    /** A file already saved to disk once; every message of the send points to the same stored file. */
    public static final class StoredAttachment {
        public final String storedFileName;
        public final String originalFileName;
        public final String contentType;
        public final long size;

        public StoredAttachment(String storedFileName, String originalFileName, String contentType, long size) {
            this.storedFileName = storedFileName;
            this.originalFileName = originalFileName;
            this.contentType = contentType;
            this.size = size;
        }
    }

    private static final class Resolved {
        List<ClassRecipientCount> targets = new ArrayList<>();
        Map<Long, Grade> grades = new HashMap<>();
        Map<Long, Section> sections = new HashMap<>();
    }

    /** Which classes (with students) would receive the message, and how many students each. Changes nothing. */
    public List<ClassRecipientCount> preview(Long schoolId, Long mediumId, List<Long> gradeIds, List<Long> sectionIds) {
        return resolve(schoolId, mediumId, gradeIds, sectionIds).targets;
    }

    /**
     * Sends one message per class with students. Returns: classesSent, studentsReached, skipped
     * (classes without students), failed (class labels that errored). Throws IllegalArgumentException
     * for bad input or when no selected class has students.
     */
    public Map<String, Object> send(String heading, String message, Long mediumId,
                                    List<Long> gradeIds, List<Long> sectionIds,
                                    List<StoredAttachment> files, School school, UserEntity sender) {
        Resolved r = resolve(school.getId(), mediumId, gradeIds, sectionIds);
        if (r.targets.isEmpty()) {
            throw new IllegalArgumentException("No Active students found for the selected classes"
                    + (mediumId != null ? " and medium." : "."));
        }

        int sent = 0;
        long reached = 0;
        List<String> failed = new ArrayList<>();
        for (ClassRecipientCount c : r.targets) {
            try {
                sendOne(heading, message, mediumId, r.grades.get(c.getGradeId()), r.sections.get(c.getSectionId()),
                        files, school, sender);
                sent++;
                reached += c.getCount();
            } catch (Exception e) {
                log.error("Class notification failed for {}", c.getLabel(), e);
                failed.add(c.getLabel());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("classesSent", sent);
        result.put("studentsReached", reached);
        result.put("failed", failed);
        return result;
    }

    // ── internals ────────────────────────────────────────────────────────────

    private void sendOne(String heading, String message, Long mediumId, Grade grade, Section section,
                         List<StoredAttachment> files, School school, UserEntity sender) {
        SmsMessage sms = new SmsMessage();
        sms.setSmsHeading(heading);
        sms.setMessageType(SmsMessage.MESSAGE_TYPE_NOTIFICATION);
        sms.setCreatedAt(new Date());
        sms.setSchool(school);
        sms.setCreatedBy(sender);
        sms.setResolution(SmsMessage.RESOLUTION_TYPE_UNRESOLVED);
        sms.setRecipientType(SmsMessage.RECIPIENT_TYPE_CLASS);
        sms.setGrade(grade);
        sms.setSection(section);

        SmsConversation conversation = new SmsConversation();
        conversation.setSmsMessage(sms);
        conversation.setContent(message);
        conversation.setSentAt(new Date());
        conversation.setSeen(true);
        conversation.setHasAttachment(!files.isEmpty());
        conversation.setHaveDocAttachment(false);
        conversation.setIsDeleted(false);
        conversation.setInitiatedBy(SmsConversation.INITIATED_BY_SCHOOL);
        sms.setConversations(Collections.singletonList(conversation));

        List<SmsMessageAttachment> attachments = new ArrayList<>();
        for (StoredAttachment f : files) {
            attachments.add(new SmsMessageAttachment(sms, f.storedFileName, f.originalFileName,
                    f.contentType, f.size, LocalDateTime.now()));
        }
        sms.setAttachments(attachments);

        smsMessageService.saveSmsMessage(sms);

        Long schoolId = school.getId();
        List<AcademicStudent> students;
        if (mediumId == null) {
            smsMessageService.materializeClassRecipients(sms.getId(), schoolId, grade.getId(), section.getId());
            students = academicStudentRepository
                    .findAllBySchool_IdAndGrade_IdAndSection_IdAndStatus(schoolId, grade.getId(), section.getId(), "Active");
        } else {
            smsMessageRepository.insertClassRecipientsByMedium(sms.getId(), schoolId, grade.getId(), section.getId(), mediumId);
            students = academicStudentRepository
                    .findAllBySchool_IdAndGrade_IdAndSection_IdAndMedium_IdAndStatus(
                            schoolId, grade.getId(), section.getId(), mediumId, "Active");
        }
        // Same push step the existing CLASS send runs after materializing recipients.
        smsMessageService.pushToRecipients(sms, students);
    }

    private Resolved resolve(Long schoolId, Long mediumId, List<Long> gradeIds, List<Long> sectionIds) {
        List<Long> gIds = cleanIds(gradeIds, "grade");
        List<Long> sIds = cleanIds(sectionIds, "section");

        Resolved r = new Resolved();
        for (Grade g : gradeService.getAllGrades()) r.grades.put(g.getId(), g);
        for (Section s : sectionService.getAllSections()) r.sections.put(s.getId(), s);
        for (Long id : gIds) {
            if (!r.grades.containsKey(id)) throw new IllegalArgumentException("Unknown grade selected.");
        }
        for (Long id : sIds) {
            if (!r.sections.containsKey(id)) throw new IllegalArgumentException("Unknown section selected.");
        }

        List<Object[]> rows = (mediumId == null)
                ? academicStudentRepository.countActiveByClasses(schoolId, gIds, sIds)
                : academicStudentRepository.countActiveByClassesAndMedium(schoolId, mediumId, gIds, sIds);
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put(((Number) row[0]).longValue() + ":" + ((Number) row[1]).longValue(), ((Number) row[2]).longValue());
        }

        // Selected order (grades first, then sections); classes without students are left out.
        for (Long gid : gIds) {
            for (Long sid : sIds) {
                Long n = counts.get(gid + ":" + sid);
                if (n != null && n > 0) {
                    r.targets.add(new ClassRecipientCount(gid, sid,
                            r.grades.get(gid).getGradeName(), r.sections.get(sid).getSectionName(), n));
                }
            }
        }
        return r;
    }

    private static List<Long> cleanIds(List<Long> ids, String what) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Please select at least one " + what + ".");
        }
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) set.add(id);
        }
        if (set.isEmpty()) throw new IllegalArgumentException("Please select at least one " + what + ".");
        if (set.size() > MAX_IDS) throw new IllegalArgumentException("Too many " + what + "s selected.");
        return new ArrayList<>(set);
    }
}
