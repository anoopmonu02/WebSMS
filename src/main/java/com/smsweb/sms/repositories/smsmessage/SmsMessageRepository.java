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

    @Query("SELECT DISTINCT m FROM SmsMessage m " +
            "LEFT JOIN m.recipients r " +
            "WHERE m.messageType = '" + SmsMessage.MESSAGE_TYPE_NOTIFICATION + "' " +
            "AND ( " +
            "    (m.recipientType = '" + SmsMessage.RECIPIENT_TYPE_STUDENT + "' AND r.id = :studentId) " +
            "    OR (m.recipientType = '" + SmsMessage.RECIPIENT_TYPE_CLASS + "' " +
            "        AND m.grade.id = (SELECT s.grade.id FROM AcademicStudent s WHERE s.id = :studentId) " +
            "        AND m.section.id = (SELECT s.section.id FROM AcademicStudent s WHERE s.id = :studentId)) " +
            "    OR m.recipientType = '" + SmsMessage.RECIPIENT_TYPE_ALL + "' " +
            ") " +
            "ORDER BY m.createdAt DESC")
    List<SmsMessage> findByRecipientId(@Param("studentId") Long studentId);

    @Query("SELECT m FROM SmsMessage m WHERE m.grade.id = ?1 AND m.section.id = ?2 AND m.messageType = ?3")
    List<SmsMessage> findByGradeIdAndSectionIdAndMessageType(Long gradeId, Long sectionId, String messageType);

}
