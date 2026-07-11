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

    /** Plain-text (non-HTML) rendering of the same reminder content shown/printed on the Fee Reminder screen. */
    private String buildFeeReminderContent(String language, AcademicStudent as, String amount, String monthsList,
                                            String headList, String lastdate, School school) {
        boolean hindi = "hi".equalsIgnoreCase(language);
        Student student = as.getStudent();
        String studentName = student != null && student.getStudentName() != null ? student.getStudentName() : "";
        String fatherName = student != null && student.getFatherName() != null ? student.getFatherName() : "";
        String motherName = student != null && student.getMotherName() != null ? student.getMotherName() : "";
        String grade = as.getGrade() != null ? as.getGrade().getGradeName() : "";
        String section = as.getSection() != null ? as.getSection().getSectionName() : "";
        String schoolName = school != null && school.getSchoolName() != null ? school.getSchoolName() : "";
        String schoolAddress = school != null && school.getAddress() != null ? school.getAddress() : "";
        String formattedDate = new SimpleDateFormat("dd/MMM/yyyy").format(new Date());

        StringBuilder sb = new StringBuilder();
        sb.append(schoolName).append("\n");
        if (!schoolAddress.isBlank()) sb.append(schoolAddress).append("\n");

        if (hindi) {
            sb.append("मासिक शुल्क सूचना\n\n");
            sb.append("छात्र का नाम: ").append(studentName)
                    .append("   पिता का नाम: ").append(fatherName)
                    .append("   माता का नाम: ").append(motherName)
                    .append("   कक्षा: ").append(grade)
                    .append("   वर्ग: ").append(section).append("\n\n");
            sb.append("अभिभावक महोदय, आपके पाल्य/पाल्या का निम्नलिखित शुल्क जमा होना शेष है| अतः दिनांकः ")
                    .append(lastdate)
                    .append(" तक विद्यालय समय में निर्धारित शुल्क जमा कराने का कष्ट करें| उपरोक्त तिथि के बाद जमा होने वाला शुल्क बिना विलम्ब शुल्क के जमा नहीं किया जायेगा|\n\n");
            sb.append("इस माह तक जमा होने वाला कुल मासिक शुल्क: ₹ ").append(amount).append("\n");
            sb.append("सम्मिलित माह: ").append(monthsList).append("\n");
            sb.append("सम्मिलित मद: ").append(headList).append("\n\n");
            sb.append("दिनांक: ").append(formattedDate).append("                    प्रधानाचार्य");
        } else {
            sb.append("Monthly Fees Reminder\n\n");
            sb.append("Student's Name: ").append(studentName)
                    .append("   Father's Name: ").append(fatherName)
                    .append("   Mother's Name: ").append(motherName)
                    .append("   Class: ").append(grade)
                    .append("   Section: ").append(section).append("\n\n");
            sb.append("Dear Parent/Guardian, the following fee for your ward is currently outstanding. Therefore, you are requested to kindly deposit the due amount during school hours by ")
                    .append(lastdate)
                    .append(". Any payment made after this deadline will not be accepted without a late fee.\n\n");
            sb.append("Total Outstanding Monthly Fees: ₹ ").append(amount).append("\n");
            sb.append("Applicable Months: ").append(monthsList).append("\n");
            sb.append("Fee Components Included: ").append(headList).append("\n\n");
            sb.append("Date: ").append(formattedDate).append("                    Principal");
        }
        return sb.toString();
    }

}
