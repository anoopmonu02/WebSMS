package com.smsweb.sms.services.mobile;

import com.smsweb.sms.models.messaging.SmsMessage;
import com.smsweb.sms.models.messaging.SmsMessageReadStatus;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.repositories.smsmessage.SmsMessageReadStatusRepository;
import com.smsweb.sms.repositories.smsmessage.SmsMessageRepository;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * NEW service (feature #5), brand-new package (services.mobile) — mobile-only.
 * Does NOT modify SmsMessageService.java. Injects the existing
 * SmsMessageRepository / AcademicStudentRepository read-only (both already
 * public beans, no changes needed to either) plus the new
 * SmsMessageReadStatusRepository it owns outright.
 */
@Service
public class MobileNotificationReadService {

    @Autowired
    private SmsMessageReadStatusRepository smsMessageReadStatusRepository;

    // Existing repositories — injected read-only, never modified by this class.
    @Autowired
    private SmsMessageRepository smsMessageRepository;

    @Autowired
    private AcademicStudentRepository academicStudentRepository;

    /** Returns the set of sms_message ids this student has actually marked read. */
    public Set<Long> getReadMessageIds(Long academicStudentId) {
        return new HashSet<>(smsMessageReadStatusRepository.findReadMessageIdsForStudent(academicStudentId));
    }

    /**
     * Marks a single notification as read for this student. Idempotent.
     *
     * SECURITY: the caller (controller) MUST verify academicStudentId is an
     * actual recipient of smsMessageId before calling this — see
     * MobileNotificationController's ownership check.
     */
    @Transactional
    public void markNotificationAsRead(Long smsMessageId, Long academicStudentId) {
        Optional<SmsMessageReadStatus> existing =
                smsMessageReadStatusRepository.findBySmsMessage_IdAndAcademicStudent_Id(smsMessageId, academicStudentId);

        if (existing.isPresent()) {
            if (!existing.get().isRead()) {
                existing.get().setRead(true);
                existing.get().setReadAt(LocalDateTime.now());
                smsMessageReadStatusRepository.save(existing.get());
            }
            return;
        }

        SmsMessage message = smsMessageRepository.findById(smsMessageId)
                .orElseThrow(() -> new IllegalArgumentException("SmsMessage not found: " + smsMessageId));
        AcademicStudent academicStudent = academicStudentRepository.findById(academicStudentId)
                .orElseThrow(() -> new IllegalArgumentException("AcademicStudent not found: " + academicStudentId));

        SmsMessageReadStatus status = new SmsMessageReadStatus(message, academicStudent);
        status.setRead(true);
        status.setReadAt(LocalDateTime.now());
        smsMessageReadStatusRepository.save(status);
    }

    /**
     * Marks every message id in `visibleMessageIds` as read for this student.
     * The controller passes in ids from SmsMessageService.getNotificationsByStudentId()
     * (the existing, unmodified service call) so this class never needs its own
     * dependency on message-visibility rules.
     */
    @Transactional
    public void markAllAsRead(Long academicStudentId, List<Long> visibleMessageIds) {
        LocalDateTime now = LocalDateTime.now();

        // 1) Flip any existing unread rows to read.
        smsMessageReadStatusRepository.markAllExistingAsRead(academicStudentId, now);

        // 2) Create read rows for messages that have no row at all yet.
        Set<Long> alreadyTracked = getReadMessageIds(academicStudentId);
        AcademicStudent academicStudent = academicStudentRepository.findById(academicStudentId)
                .orElseThrow(() -> new IllegalArgumentException("AcademicStudent not found: " + academicStudentId));

        List<SmsMessageReadStatus> toCreate = visibleMessageIds.stream()
                .filter(id -> !alreadyTracked.contains(id))
                .filter(id -> smsMessageReadStatusRepository
                        .findBySmsMessage_IdAndAcademicStudent_Id(id, academicStudentId).isEmpty())
                .map(id -> smsMessageRepository.findById(id).orElse(null))
                .filter(m -> m != null)
                .map(m -> {
                    SmsMessageReadStatus s = new SmsMessageReadStatus(m, academicStudent);
                    s.setRead(true);
                    s.setReadAt(now);
                    return s;
                })
                .collect(Collectors.toList());

        if (!toCreate.isEmpty()) {
            smsMessageReadStatusRepository.saveAll(toCreate);
        }
    }
}
