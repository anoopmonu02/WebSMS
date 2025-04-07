package com.smsweb.sms.services.smsmessage;

import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.models.messaging.SmsConversation;
import com.smsweb.sms.models.messaging.SmsMessage;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.repositories.employee.EmployeeRepository;
import com.smsweb.sms.repositories.smsmessage.SmsMessageRepository;
import com.smsweb.sms.repositories.student.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class SmsMessageService {

    @Autowired
    private SmsMessageRepository smsMessageRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    public List<SmsMessage> getMessagesByStudentId(Long studentId) {
        return smsMessageRepository.findByAcademicStudentId(studentId);
    }

    public Optional<SmsMessage> findById(Long id) {
        return smsMessageRepository.findById(id);
    }

    public List<SmsConversation> findSmsConversationBySmsMessageId(Long messageId) {
        return smsMessageRepository.findSmsConversationBySmsMessageId(messageId);
    }

}
