package com.smsweb.sms.repositories.smsmessage;

import com.smsweb.sms.models.messaging.SmsMessageAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SmsMessageAttachmentRepository extends JpaRepository<SmsMessageAttachment, Long> {

    List<SmsMessageAttachment> findAllBySmsMessage_IdOrderByIdAsc(Long smsMessageId);

    /**
     * Batch lookup for a list of message ids in one query — used by
     * SmsMessageService.getNotificationDtosByStudentId() so listing a
     * student's notification history never turns into one attachment query
     * per row.
     */
    List<SmsMessageAttachment> findAllBySmsMessage_IdInOrderByIdAsc(List<Long> smsMessageIds);
}
