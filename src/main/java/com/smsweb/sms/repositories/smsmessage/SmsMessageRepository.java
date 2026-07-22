package com.smsweb.sms.repositories.smsmessage;

import com.smsweb.sms.models.Users.UserEntity;
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

    /**
     * Dedup guard for the daily birthday-notification job (feature: birthday notifications) —
     * true if this student already has a message with this exact heading created today.
     * Protects against a duplicate "Happy Birthday" notice if the scheduled job somehow runs
     * more than once the same day (server restart mid-run, manual re-trigger, etc.).
     */
    @Query("SELECT COUNT(m) > 0 FROM SmsMessage m JOIN m.recipients r " +
            "WHERE r.id = :academicStudentId AND m.smsHeading = :heading " +
            "AND FUNCTION('DATE', m.createdAt) = CURRENT_DATE")
    boolean existsTodaysMessageForStudentAndHeading(@Param("academicStudentId") Long academicStudentId,
                                                     @Param("heading") String heading);

    @Query("select sc from SmsConversation  sc where sc.smsMessage.id=:messageId order by  sc.sentAt desc")
    List<SmsConversation> findSmsConversationBySmsMessageId(Long messageId);

    @Modifying
    @Transactional
    @Query("UPDATE SmsMessage m SET m.resolution = 'RESOLVED', m.updatedBy = :updatedBy, m.updatedAt = :updatedAt WHERE m.id = :id")
    int resolveSmsMessage(@Param("id") Long id, @Param("updatedBy") UserEntity updatedBy, @Param("updatedAt") Date updatedAt);
    // Returns number of rows updated

    /**
     * NOTE ON THE TWO BRANCHES BELOW: CLASS/ALL notifications sent AFTER the
     * recipient-materialization change (see insertClassRecipients/insertSchoolRecipients)
     * now have real rows in sms_message_recipients, exactly like STUDENT always has —
     * so `r.id = :studentId` alone correctly (and safely, school-scoped) matches them.
     * The second half of the OR is a backward-compat fallback ONLY for messages sent
     * BEFORE that change, which have an empty recipients collection and were matched
     * purely by grade/section (CLASS) or unconditionally (ALL) — kept so historical
     * notifications don't silently disappear from a student's notification list.
     * New sends never hit this fallback since m.recipients is never empty for them.
     */
    @Query("SELECT DISTINCT m FROM SmsMessage m " +
            "LEFT JOIN m.recipients r " +
            "WHERE m.messageType = '" + SmsMessage.MESSAGE_TYPE_NOTIFICATION + "' " +
            "AND ( " +
            "    r.id = :studentId " +
            "    OR (m.recipients IS EMPTY AND m.recipientType = '" + SmsMessage.RECIPIENT_TYPE_CLASS + "' " +
            "        AND m.grade.id = (SELECT s.grade.id FROM AcademicStudent s WHERE s.id = :studentId) " +
            "        AND m.section.id = (SELECT s.section.id FROM AcademicStudent s WHERE s.id = :studentId)) " +
            "    OR (m.recipients IS EMPTY AND m.recipientType = '" + SmsMessage.RECIPIENT_TYPE_ALL + "') " +
            ") " +
            "ORDER BY m.createdAt DESC")
    List<SmsMessage> findByRecipientId(@Param("studentId") Long studentId);

    /**
     * Materializes one sms_message_recipients row per currently-Active student in the
     * given grade+section of the given school — a single set-based INSERT...SELECT,
     * not a stored procedure (see design discussion: no perf benefit to a proc here,
     * this is already one round-trip either way). Fixes the cross-branch leak that a
     * grade/section-only match would have (Grade/Section are shared lookup tables
     * across schools) by scoping the SELECT to school_id explicitly.
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO sms_message_recipients (sms_message_id, academic_student_id) " +
            "SELECT :messageId, a.id FROM academic_students a " +
            "WHERE a.school_id = :schoolId AND a.grade_id = :gradeId AND a.section_id = :sectionId " +
            "AND a.status = 'Active'",
            nativeQuery = true)
    int insertClassRecipients(@Param("messageId") Long messageId, @Param("schoolId") Long schoolId,
                              @Param("gradeId") Long gradeId, @Param("sectionId") Long sectionId);

    /**
     * Same idea as insertClassRecipients, but for the "All" recipient type — one row
     * per currently-Active student in the sending school only (this is what makes
     * "All" mean "all students of this school", not "every student on the platform").
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO sms_message_recipients (sms_message_id, academic_student_id) " +
            "SELECT :messageId, a.id FROM academic_students a " +
            "WHERE a.school_id = :schoolId AND a.status = 'Active'",
            nativeQuery = true)
    int insertSchoolRecipients(@Param("messageId") Long messageId, @Param("schoolId") Long schoolId);

    @Query("SELECT m FROM SmsMessage m WHERE m.grade.id = ?1 AND m.section.id = ?2 AND m.messageType = ?3")
    List<SmsMessage> findByGradeIdAndSectionIdAndMessageType(Long gradeId, Long sectionId, String messageType);

    @Query("SELECT m FROM SmsMessage m JOIN m.recipients r WHERE r.id = :studentId AND m.messageType = :messageType ORDER BY m.createdAt DESC")
    List<SmsMessage> findByRecipients_IdAndMessageType(@Param("studentId") Long studentId, @Param("messageType") String messageType);

    /** Reschedule an activity's follow-up date. Scoped to ACTIVITIES rows only so it can never touch a complaint/notification row. */
    @Modifying
    @Transactional
    @Query("UPDATE SmsMessage m SET m.dueDate = :dueDate WHERE m.id = :id AND m.messageType = '" + SmsMessage.MESSAGE_TYPE_ACTIVITIES + "'")
    int updateActivityDueDate(@Param("id") Long id, @Param("dueDate") Date dueDate);

}
