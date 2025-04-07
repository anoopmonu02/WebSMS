package com.smsweb.sms.repositories.smsmessage;

import com.smsweb.sms.models.messaging.SmsConversation;
import com.smsweb.sms.models.messaging.SmsMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SmsMessageRepository extends JpaRepository<SmsMessage, Long> {

    @Query("SELECT m FROM SmsMessage m JOIN m.recipients r WHERE r.id = :studentId ORDER BY m.sentAt DESC")
    List<SmsMessage> findByAcademicStudentId(Long studentId);

    @Query("select sc from SmsConversation  sc where sc.smsMessage.id=:messageId order by  sc.sentAt desc")
    List<SmsConversation> findSmsConversationBySmsMessageId(Long messageId);
}
