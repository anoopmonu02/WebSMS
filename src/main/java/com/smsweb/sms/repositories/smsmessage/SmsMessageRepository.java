package com.smsweb.sms.repositories.smsmessage;

import com.smsweb.sms.models.messaging.SmsConversation;
import com.smsweb.sms.models.messaging.SmsMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

public interface SmsMessageRepository extends JpaRepository<SmsMessage, Long> {

    @Query("SELECT m FROM SmsMessage m JOIN m.recipients r WHERE r.id = :studentId ORDER BY m.createdAt DESC")
    List<SmsMessage> findByRecipients_Id(Long studentId);

    @Query("select sc from SmsConversation  sc where sc.smsMessage.id=:messageId order by  sc.sentAt desc")
    List<SmsConversation> findSmsConversationBySmsMessageId(Long messageId);

    @Modifying
    @Transactional
    @Query("UPDATE SmsMessage m SET m.resolution = 'RESOLVED', m.updatedBy = :updatedBy, m.updatedAt = :updatedAt WHERE m.id = :id")
    int resolveSmsMessage(@Param("id") Long id, @Param("updatedBy") String updatedBy, @Param("updatedAt") Date updatedAt);
    // Returns number of rows updated
}
