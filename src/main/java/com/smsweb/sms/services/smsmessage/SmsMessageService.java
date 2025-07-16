package com.smsweb.sms.services.smsmessage;

import com.smsweb.sms.dto.SmsNotificationDto;
import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.models.messaging.SmsConversation;
import com.smsweb.sms.models.messaging.SmsMessage;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.repositories.employee.EmployeeRepository;
import com.smsweb.sms.repositories.smsmessage.SmsConversationRepository;
import com.smsweb.sms.repositories.smsmessage.SmsMessageRepository;
import com.smsweb.sms.repositories.student.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class SmsMessageService {

    @Autowired
    private SmsMessageRepository smsMessageRepository;

    @Autowired
    private SmsConversationRepository smsConversationRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    public List<SmsMessage> getMessagesByStudentId(Long studentId) {
        return smsMessageRepository.findByRecipients_Id(studentId);
    }

    public Optional<SmsMessage> findById(Long id) {
        return smsMessageRepository.findById(id);
    }

    public List<SmsConversation> findSmsConversationBySmsMessageId(Long messageId) {
        return smsMessageRepository.findSmsConversationBySmsMessageId(messageId);
    }

    public SmsConversation saveSmsConversation(SmsConversation conversation) {
        return smsConversationRepository.save(conversation);
    }

    public Optional<SmsMessage> resolveSmsMessage(Long id, String updatedBY) {
        int updated = smsMessageRepository.resolveSmsMessage(id, updatedBY, new Date());
        if (updated > 0) {
            return smsMessageRepository.findById(id);
        } else {
            return Optional.empty();
        }
    }

    public SmsMessage saveSmsMessage(SmsMessage smsMessage) {
        if (smsMessage.getConversations() != null) {
            smsMessage.getConversations().forEach(convo -> convo.setSmsMessage(smsMessage));
        }
        return smsMessageRepository.save(smsMessage);
    }

    public void saveConversation(SmsConversation conversation) {
        smsConversationRepository.save(conversation);
    }

    public void saveAllConversations(List<SmsConversation> conversations) {
        smsConversationRepository.saveAll(conversations); // efficient batch save
    }
    public List<SmsMessage> getNotificationsByStudentId(Long studentId) {
        return smsMessageRepository.findByRecipientId(studentId);
    }

    /*public List<SmsMessage> getNotificationsByGradeAndSection(Long gradeId, Long sectionId) {
        return smsMessageRepository.findByGradeIdAndSectionIdAndMessageType(gradeId, sectionId, "NOTIFICATION");
    }*/


    public List<SmsNotificationDto> getNotificationDtosByStudentId(Long studentId) {
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


}
