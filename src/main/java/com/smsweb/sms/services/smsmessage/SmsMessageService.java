package com.smsweb.sms.services.smsmessage;

import com.smsweb.sms.dto.SmsNotificationDto;
import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.messaging.SmsConversation;
import com.smsweb.sms.models.messaging.SmsMessage;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.repositories.employee.EmployeeRepository;
import com.smsweb.sms.repositories.smsmessage.SmsConversationRepository;
import com.smsweb.sms.repositories.smsmessage.SmsMessageRepository;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.repositories.student.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
public class SmsMessageService {
    private static final Logger log = LoggerFactory.getLogger(SmsMessageService.class);


    @Autowired
    private SmsMessageRepository smsMessageRepository;

    @Autowired
    private SmsConversationRepository smsConversationRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AcademicStudentRepository academicStudentRepository;

    public List<SmsMessage> getMessagesByStudentId(Long studentId) {
        return smsMessageRepository.findByRecipients_IdAndMessageType(studentId, SmsMessage.MESSAGE_TYPE_COMPLAINT);
    }

    public Optional<SmsMessage> findById(Long id) {
        log.info("Inside findById");
        return smsMessageRepository.findById(id);
    }

    public List<SmsConversation> findSmsConversationBySmsMessageId(Long messageId) {
        log.info("Inside findSmsConversationBySmsMessageId");
        return smsMessageRepository.findSmsConversationBySmsMessageId(messageId);
    }

    public SmsConversation saveSmsConversation(SmsConversation conversation) {
        log.info("Inside saveSmsConversation");
        return smsConversationRepository.save(conversation);
    }

    public Optional<SmsMessage> resolveSmsMessage(Long id, UserEntity updatedBy) {
        log.info("Inside resolveSmsMessage");
        int updated = smsMessageRepository.resolveSmsMessage(id, updatedBy, new Date());
        if (updated > 0) {
            return smsMessageRepository.findById(id);
        } else {
            return Optional.empty();
        }
    }

    public SmsMessage saveSmsMessage(SmsMessage smsMessage) {
        log.info("Inside saveSmsMessage");
        if (smsMessage.getConversations() != null) {
            smsMessage.getConversations().forEach(convo -> convo.setSmsMessage(smsMessage));
        }
        return smsMessageRepository.save(smsMessage);
    }

    public void saveConversation(SmsConversation conversation) {
        log.info("Inside saveConversation");
        smsConversationRepository.save(conversation);
    }

    public void saveAllConversations(List<SmsConversation> conversations) {
        log.info("Inside saveAllConversations");
        smsConversationRepository.saveAll(conversations); // efficient batch save
    }
    public List<SmsMessage> getNotificationsByStudentId(Long studentId) {
        return smsMessageRepository.findByRecipientId(studentId);
    }

    /**
     * Materializes one sms_message_recipients row per currently-Active student in the
     * given grade+section of the given school, for a CLASS-recipient notification that
     * was just saved (messageId must already exist). See SmsMessageRepository.insertClassRecipients
     * for why this is a plain INSERT...SELECT rather than a stored procedure.
     */
    public int materializeClassRecipients(Long messageId, Long schoolId, Long gradeId, Long sectionId) {
        log.info("Inside materializeClassRecipients — messageId={}, schoolId={}, gradeId={}, sectionId={}",
                messageId, schoolId, gradeId, sectionId);
        return smsMessageRepository.insertClassRecipients(messageId, schoolId, gradeId, sectionId);
    }

    /**
     * Same as materializeClassRecipients, but for an ALL-recipient ("whole school")
     * notification — one row per currently-Active student in the given school.
     */
    public int materializeSchoolRecipients(Long messageId, Long schoolId) {
        log.info("Inside materializeSchoolRecipients — messageId={}, schoolId={}", messageId, schoolId);
        return smsMessageRepository.insertSchoolRecipients(messageId, schoolId);
    }

    public List<SmsMessage> getActivitiesByStudentId(Long studentId) {
        return smsMessageRepository.findByRecipients_IdAndMessageType(studentId, SmsMessage.MESSAGE_TYPE_ACTIVITIES);
    }

    /** Reschedule an activity's follow-up date. Returns rows-updated (0 if the id doesn't exist or isn't an ACTIVITIES row). */
    public int updateActivityDueDate(Long id, Date dueDate) {
        log.info("Inside updateActivityDueDate — id={}", id);
        return smsMessageRepository.updateActivityDueDate(id, dueDate);
    }

    /*public List<SmsMessage> getNotificationsByGradeAndSection(Long gradeId, Long sectionId) {
        return smsMessageRepository.findByGradeIdAndSectionIdAndMessageType(gradeId, sectionId, "NOTIFICATION");
    }*/


    public List<SmsNotificationDto> getNotificationDtosByStudentId(Long studentId) {
        log.info("Inside getNotificationDtosByStudentId");
        List<SmsMessage> messages = smsMessageRepository.findByRecipientId(studentId);
        List<SmsNotificationDto> dtos = new ArrayList<>();

        for (SmsMessage msg : messages) {
            SmsNotificationDto dto = new SmsNotificationDto();

            if ("STUDENT".equals(msg.getRecipientType())) {
                AcademicStudent stu = msg.getRecipients() != null && !msg.getRecipients().isEmpty()
                        ? msg.getRecipients().get(0)
                        : null;
                if (stu != null) {
                    dto.setClassName(stu.getGrade() != null ? stu.getGrade().getGradeName() : "");
                    dto.setSectionName(stu.getSection() != null ? stu.getSection().getSectionName() : "");
                }
            } else if ("CLASS".equals(msg.getRecipientType())) {
                dto.setClassName(msg.getGrade() != null ? msg.getGrade().getGradeName() : "");
                dto.setSectionName(msg.getSection() != null ? msg.getSection().getSectionName() : "");
            } else {
                dto.setClassName(""); // for ALL
                dto.setSectionName("");
            }

            dto.setRecipientType(msg.getRecipientType());
            dto.setSmsHeading(msg.getSmsHeading());

            if (msg.getConversations() != null && !msg.getConversations().isEmpty()) {
                dto.setSmsContent(msg.getConversations().get(0).getContent());
            } else {
                dto.setSmsContent("");
            }

            dto.setSmsDate(msg.getCreatedAt());
            dtos.add(dto);
        }
        return dtos;
    }

    /**
     * Batch-creates one STUDENT-recipient notification per row, reusing the exact same
     * amount/months/heads that were already shown/reviewed on the Fee Reminder screen
     * (no recalculation) — per the agreed design: the admin reviews the on-screen figures
     * and this persists exactly what was reviewed, rather than silently recomputing.
     * Still validates ownership server-side: an academicStudentId is only accepted if it
     * actually belongs to the given school + academic year, so a malformed/tampered
     * payload can't write a notification against the wrong student.
     */
    public Map<String, Object> sendFeeReminderNotifications(List<Map<String, Object>> students, String language,
                                                              String lastdate, School school, AcademicYear academicYear,
                                                              UserEntity loggedInUser) {
        log.info("Inside sendFeeReminderNotifications — count={}", students == null ? 0 : students.size());
        int saved = 0;
        int skipped = 0;
        List<String> skippedReasons = new ArrayList<>();

        if (students != null) {
            for (Map<String, Object> row : students) {
                Object rawId = row.get("academicStudentId");
                try {
                    if (rawId == null) {
                        skipped++;
                        skippedReasons.add("Row missing academicStudentId");
                        continue;
                    }
                    Long academicStudentId = Long.valueOf(rawId.toString());
                    Optional<AcademicStudent> asOpt = academicStudentRepository.findById(academicStudentId);
                    if (asOpt.isEmpty()) {
                        skipped++;
                        skippedReasons.add("Student id " + academicStudentId + " not found");
                        continue;
                    }
                    AcademicStudent as = asOpt.get();
                    boolean belongsToSchool = as.getSchool() != null && school != null && as.getSchool().getId().equals(school.getId());
                    boolean belongsToYear = as.getAcademicYear() != null && academicYear != null && as.getAcademicYear().getId().equals(academicYear.getId());
                    if (!belongsToSchool || !belongsToYear) {
                        skipped++;
                        skippedReasons.add("Student id " + academicStudentId + " does not belong to the current school/session");
                        continue;
                    }

                    String amount = row.get("amount") != null ? row.get("amount").toString() : "0";
                    String monthsList = row.get("monthsList") != null ? row.get("monthsList").toString() : "";
                    String headList = row.get("headList") != null ? row.get("headList").toString() : "";

                    String heading = "hi".equalsIgnoreCase(language) ? "मासिक शुल्क सूचना" : "Monthly Fees Reminder";
                    String content = buildFeeReminderContent(language, as, amount, monthsList, headList, lastdate, school);

                    SmsConversation conversation = new SmsConversation();
                    conversation.setContent(content);
                    conversation.setInitiatedBy(SmsConversation.INITIATED_BY_SCHOOL);
                    conversation.setSeen(true);
                    conversation.setIsDeleted(false);
                    conversation.setSentAt(new Date());
                    conversation.setHasAttachment(false);
                    conversation.setHaveDocAttachment(false);

                    SmsMessage smsMessage = new SmsMessage();
                    smsMessage.setSmsHeading(heading);
                    smsMessage.setMessageType(SmsMessage.MESSAGE_TYPE_NOTIFICATION);
                    smsMessage.setRecipientType(SmsMessage.RECIPIENT_TYPE_STUDENT);
                    smsMessage.setResolution(SmsMessage.RESOLUTION_TYPE_UNRESOLVED);
                    smsMessage.setSchool(school);
                    smsMessage.setCreatedBy(loggedInUser);
                    smsMessage.setCreatedAt(new Date());
                    smsMessage.setRecipients(Collections.singletonList(as));
                    smsMessage.setConversations(new ArrayList<>(List.of(conversation)));

                    saveSmsMessage(smsMessage);
                    saved++;
                } catch (Exception e) {
                    log.error("Failed to save fee reminder notification for academicStudentId={}", rawId, e);
                    skipped++;
                    skippedReasons.add("Student id " + rawId + " failed: " + e.getMessage());
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("saved", saved);
        result.put("skipped", skipped);
        result.put("skippedReasons", skippedReasons);
        return result;
    }

    /**
     * Content saved into the notification/conversation record for a fee reminder.
     * Deliberately does NOT include the school name/address, the student/father/
     * mother/class/section line, or a "Principal"/"प्रधानाचार्य" signature — those
     * belong to the printed/on-screen reminder slip (built separately, client-side,
     * in feereminder.html) and are personalized per student there; this method only
     * builds the generic message body that gets stored once per notification.
     */
    private String buildFeeReminderContent(String language, AcademicStudent as, String amount, String monthsList,
                                            String headList, String lastdate, School school) {
        boolean hindi = "hi".equalsIgnoreCase(language);
        String formattedDate = new SimpleDateFormat("dd/MMM/yyyy").format(new Date());

        StringBuilder sb = new StringBuilder();
        if (hindi) {
            sb.append("मासिक शुल्क सूचना\n\n");
            sb.append("अभिभावक महोदय, आपके पाल्य/पाल्या का निम्नलिखित शुल्क जमा होना शेष है| अतः दिनांकः ")
                    .append(lastdate)
                    .append(" तक विद्यालय समय में निर्धारित शुल्क जमा कराने का कष्ट करें| उपरोक्त तिथि के बाद जमा होने वाला शुल्क बिना विलम्ब शुल्क के जमा नहीं किया जायेगा|\n");
            sb.append("इस माह तक जमा होने वाला कुल मासिक शुल्क: ₹ ").append(amount).append("\n");
            sb.append("सम्मिलित माह: ").append(monthsList).append("\n");
            sb.append("सम्मिलित मद: ").append(headList).append("\n");
            sb.append("दिनांक: ").append(formattedDate);
        } else {
            sb.append("Monthly Fees Reminder\n\n");
            sb.append("Dear Parent/Guardian, the following fee for your ward is currently outstanding. Therefore, you are requested to kindly deposit the due amount during school hours by ")
                    .append(lastdate)
                    .append(". Any payment made after this deadline will not be accepted without a late fee.\n");
            sb.append("Total Outstanding Monthly Fees: ₹ ").append(amount).append("\n");
            sb.append("Applicable Months: ").append(monthsList).append("\n");
            sb.append("Fee Components Included: ").append(headList).append("\n");
            sb.append("Date: ").append(formattedDate);
        }
        return sb.toString();
    }

}
