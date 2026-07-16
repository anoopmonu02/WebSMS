package com.smsweb.sms.repositories.smsmessage;

import com.smsweb.sms.models.messaging.SmsMessageReadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * NEW repository — backs SmsMessageReadStatus (feature #5). Placed in the
 * same package as the existing SmsMessageRepository / SmsConversationRepository
 * for consistency; it does not modify either of those files.
 */
public interface SmsMessageReadStatusRepository extends JpaRepository<SmsMessageReadStatus, Long> {

    Optional<SmsMessageReadStatus> findBySmsMessage_IdAndAcademicStudent_Id(
            Long smsMessageId, Long academicStudentId);

    @Query("SELECT r.smsMessage.id FROM SmsMessageReadStatus r " +
           "WHERE r.academicStudent.id = :academicStudentId AND r.isRead = true")
    List<Long> findReadMessageIdsForStudent(@Param("academicStudentId") Long academicStudentId);

    // Used by "mark all as read" — flips existing rows to read in one statement.
    @Modifying
    @Transactional
    @Query("UPDATE SmsMessageReadStatus r SET r.isRead = true, r.readAt = :readAt " +
           "WHERE r.academicStudent.id = :academicStudentId AND r.isRead = false")
    int markAllExistingAsRead(@Param("academicStudentId") Long academicStudentId,
                               @Param("readAt") LocalDateTime readAt);
}
