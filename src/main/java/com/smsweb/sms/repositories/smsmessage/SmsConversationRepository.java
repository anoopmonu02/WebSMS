package com.smsweb.sms.repositories.smsmessage;

import com.smsweb.sms.models.messaging.SmsConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SmsConversationRepository extends JpaRepository<SmsConversation, Long> {
    List<SmsConversation> findBySmsMessageIdOrderBySentAtAsc(Long smsMessageId);
}
