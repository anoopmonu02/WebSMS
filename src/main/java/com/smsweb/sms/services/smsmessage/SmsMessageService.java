package com.smsweb.sms.services.smsmessage;

import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.models.messaging.SmsConversation;
import com.smsweb.sms.models.messaging.SmsMessage;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.repositories.employee.EmployeeRepository;
import com.smsweb.sms.repositories.smsmessage.SmsConversationRepository;
import com.smsweb.sms.repositories.smsmessage.SmsMessageRepository;
import com.smsweb.sms.repositories.student.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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

}
