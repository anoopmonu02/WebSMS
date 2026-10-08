package com.smsweb.sms.services.tc;

import com.smsweb.sms.dto.tc.TimelineChainRow;
import com.smsweb.sms.dto.tc.TimelineDto;
import com.smsweb.sms.dto.tc.TimelineRowDto;
import com.smsweb.sms.dto.tc.TimelineSessionDto;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.tc.DueClearanceReceipt;
import com.smsweb.sms.models.tc.StudentExecution;
import com.smsweb.sms.models.tc.StudentExit;
import com.smsweb.sms.models.tc.StudentLink;
import com.smsweb.sms.models.tc.TransferCertificate;
import com.smsweb.sms.models.tc.enums.EventType;
import com.smsweb.sms.models.tc.enums.RecordStatus;
import com.smsweb.sms.models.tc.enums.RefType;
import com.smsweb.sms.repositories.tc.DueClearanceReceiptRepository;
import com.smsweb.sms.repositories.tc.StudentExecutionRepository;
import com.smsweb.sms.repositories.tc.StudentExitRepository;
import com.smsweb.sms.repositories.tc.StudentLinkRepository;
import com.smsweb.sms.repositories.tc.StudentLookupRepository;
import com.smsweb.sms.repositories.tc.TransferCertificateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only student timeline (plan page P4). Shows every recorded event of a student and, when the
 * student was re-registered in a later session, of the old and new records together. Rows are grouped
 * by session, oldest first; a voided row is shown struck through.
 *
 * <p>The chain of linked records is followed in both directions with a depth cap, and each record is
 * visited once, so a bad link can never cause a loop.</p>
 */
@Service
public class StudentTimelineService {

    private static final Logger log = LoggerFactory.getLogger(StudentTimelineService.class);

    /** How many links are followed from the opened record in each direction. */
    static final int MAX_DEPTH = 20;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MMM/yyyy");

    private final StudentLookupRepository studentLookupRepository;
    private final StudentLinkRepository studentLinkRepository;
    private final StudentExecutionRepository studentExecutionRepository;
    private final StudentExitRepository studentExitRepository;
    private final TransferCertificateRepository transferCertificateRepository;
    private final DueClearanceReceiptRepository receiptRepository;

    public StudentTimelineService(StudentLookupRepository studentLookupRepository,
                                  StudentLinkRepository studentLinkRepository,
                                  StudentExecutionRepository studentExecutionRepository,
                                  StudentExitRepository studentExitRepository,
                                  TransferCertificateRepository transferCertificateRepository,
                                  DueClearanceReceiptRepository receiptRepository) {
        this.studentLookupRepository = studentLookupRepository;
        this.studentLinkRepository = studentLinkRepository;
        this.studentExecutionRepository = studentExecutionRepository;
        this.studentExitRepository = studentExitRepository;
        this.transferCertificateRepository = transferCertificateRepository;
        this.receiptRepository = receiptRepository;
    }

    @Transactional(readOnly = true)
    public TimelineDto timeline(UUID studentUuid, School school) {
        log.info("Inside timeline: student={}", studentUuid);
        if (studentUuid == null || school == null) {
            throw new TcValidationException("This student was not found.");
        }
        Student focus = studentLookupRepository.findStudentByUuid(studentUuid, school.getId())
                .orElseThrow(() -> new TcValidationException("This student was not found."));

        List<Student> chain = chainOf(focus, school.getId());
        List<Long> ids = new ArrayList<>();
        for (Student s : chain) {
            ids.add(s.getId());
        }

        TimelineDto dto = new TimelineDto();
        dto.setStudentName(focus.getStudentName());
        dto.setFatherName(focus.getFatherName());
        dto.setRegistrationNo(focus.getRegistrationNo());
        for (Student s : chain) {
            dto.getChain().add(new TimelineChainRow(s.getUuid(), s.getStudentName(), s.getRegistrationNo(),
                    s.getStatus(), s.getPsrn(), chain.size() > 1 ? lastSrNo(s) : null,
                    s.getId().equals(focus.getId())));
        }

        List<StudentExecution> events = studentExecutionRepository.findTimeline(ids, school.getId());
        dto.setTotalRows(events.size());
        dto.setSessions(group(events));
        return dto;
    }

    // ───────────────────────────── chain ─────────────────────────────

    /** The opened record plus every record linked to it, oldest first. Each record appears once. */
    private List<Student> chainOf(Student focus, Long schoolId) {
        Set<Long> seen = new HashSet<>();
        seen.add(focus.getId());

        // Older records: the link that created this record points at the previous one.
        Deque<Student> older = new ArrayDeque<>();
        Student cursor = focus;
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            StudentLink link = studentLinkRepository.findByNewStudent_Id(cursor.getId()).orElse(null);
            if (link == null) {
                break;
            }
            Student previous = link.getOldStudent();
            if (previous == null || !seen.add(previous.getId()) || !sameSchool(previous, schoolId)) {
                break;
            }
            older.addFirst(previous);
            cursor = previous;
        }

        // Newer records: every record created from this one (breadth first, capped).
        List<Student> newer = new ArrayList<>();
        Deque<Student> queue = new ArrayDeque<>();
        queue.add(focus);
        int steps = 0;
        while (!queue.isEmpty() && steps < MAX_DEPTH) {
            steps++;
            Student from = queue.poll();
            for (StudentLink link : studentLinkRepository.findByOldStudent_Id(from.getId())) {
                Student next = link.getNewStudent();
                if (next != null && seen.add(next.getId()) && sameSchool(next, schoolId)) {
                    newer.add(next);
                    queue.add(next);
                }
            }
        }

        List<Student> all = new ArrayList<>(older);
        all.add(focus);
        all.addAll(newer);
        return all;
    }

    /** SR number kept for a linked record: the one saved at re-registration for an older record, the current one for the newest. */
    private String lastSrNo(Student s) {
        List<StudentLink> outgoing = studentLinkRepository.findByOldStudent_Id(s.getId());
        if (!outgoing.isEmpty()) {
            return outgoing.get(0).getOldClassSrNo();
        }
        StudentLink incoming = studentLinkRepository.findByNewStudent_Id(s.getId()).orElse(null);
        if (incoming != null && incoming.getNewAcademicStudent() != null) {
            return incoming.getNewAcademicStudent().getClassSrNo();
        }
        return null;
    }

    private static boolean sameSchool(Student s, Long schoolId) {
        return s.getSchool() != null && schoolId.equals(s.getSchool().getId());
    }

    // ───────────────────────────── rows ─────────────────────────────

    private List<TimelineSessionDto> group(List<StudentExecution> events) {
        Map<RefType, Set<Long>> wanted = new HashMap<>();
        for (StudentExecution e : events) {
            if (e.getRefType() != null && e.getRefId() != null) {
                wanted.computeIfAbsent(e.getRefType(), k -> new LinkedHashSet<>()).add(e.getRefId());
            }
        }
        Map<Long, UUID> exitUuids = new HashMap<>();
        if (wanted.containsKey(RefType.EXIT)) {
            for (StudentExit x : studentExitRepository.findAllById(wanted.get(RefType.EXIT))) {
                exitUuids.put(x.getId(), x.getUuid());
            }
        }
        Map<Long, UUID> tcUuids = new HashMap<>();
        if (wanted.containsKey(RefType.TC)) {
            for (TransferCertificate t : transferCertificateRepository.findAllById(wanted.get(RefType.TC))) {
                tcUuids.put(t.getId(), t.getUuid());
            }
        }
        Map<Long, UUID> receiptUuids = new HashMap<>();
        if (wanted.containsKey(RefType.RECEIPT)) {
            for (DueClearanceReceipt r : receiptRepository.findAllById(wanted.get(RefType.RECEIPT))) {
                receiptUuids.put(r.getId(), r.getUuid());
            }
        }

        Map<String, List<TimelineRowDto>> bySession = new LinkedHashMap<>();
        for (StudentExecution e : events) {
            String kind = null;
            UUID target = null;
            if (e.getRefType() == RefType.EXIT) {
                kind = "EXIT";
                target = exitUuids.get(e.getRefId());
            } else if (e.getRefType() == RefType.TC) {
                kind = "TC";
                target = tcUuids.get(e.getRefId());
            } else if (e.getRefType() == RefType.RECEIPT) {
                kind = "RECEIPT";
                target = receiptUuids.get(e.getRefId());
            }
            if (target == null) {
                kind = null;
            }
            String session = e.getAcademicYear() == null ? "Session not recorded" : e.getAcademicYear().getSessionFormat();
            String className = e.getGrade() == null ? null
                    : e.getSection() == null ? e.getGrade().getGradeName()
                    : e.getGrade().getGradeName() + " " + e.getSection().getSectionName();
            boolean voided = e.getRecordStatus() == RecordStatus.VOIDED;
            bySession.computeIfAbsent(session, k -> new ArrayList<>()).add(new TimelineRowDto(
                    e.getEventDate(), e.getRecordedAt(), title(e.getEventType()), tone(e.getEventType()), describe(e),
                    className, e.getStudent().getStudentName(), e.getStudent().getRegistrationNo(),
                    e.getCreatedBy() == null ? null : e.getCreatedBy().getUsername(),
                    voided, voided ? e.getVoidReason() : null, kind, target));
        }
        List<TimelineSessionDto> out = new ArrayList<>();
        for (Map.Entry<String, List<TimelineRowDto>> en : bySession.entrySet()) {
            out.add(new TimelineSessionDto(en.getKey(), en.getValue()));
        }
        return out;
    }

    // ───────────────────────────── wording ─────────────────────────────

    static String title(EventType type) {
        switch (type) {
            case STRUCK_OFF: return "Struck off";
            case LOGIN_DISABLED: return "Login switched off";
            case LOGIN_ENABLED: return "Login switched on";
            case DUES_MANUAL: return "Dues set by hand";
            case TC_APPROVED: return "Approved, dues fixed";
            case DUES_PAID: return "Dues paid";
            case DUES_WAIVED: return "Dues waived";
            case DUES_RECEIPT_VOIDED: return "Receipt voided";
            case TC_ISSUED: return "TC issued";
            case TC_REISSUED: return "TC re-issued";
            case STRIKE_OFF_CANCELLED: return "Strike-off cancelled";
            case REACTIVATED: return "Reactivated";
            case RE_REGISTERED: return "Registered again";
            case REGISTRATION_NO_ASSIGNED: return "Registration number given";
            case SR_ASSIGNED: return "SR number given";
            case SR_CHANGED: return "SR number changed";
            case ADMISSION_TC_SUBMITTED: return "TC submitted at admission";
            case ADMISSION_NOTARY: return "Notary affidavit at admission";
            case ADMISSION_EXEMPTED: return "Admitted without TC";
            case DEACTIVATED: return "Deactivated";
            case ACTIVATED: return "Activated";
            default: return type.name();
        }
    }

    static String tone(EventType type) {
        switch (type) {
            case STRUCK_OFF:
            case LOGIN_DISABLED:
            case DUES_RECEIPT_VOIDED:
            case DEACTIVATED:
                return "danger";
            case LOGIN_ENABLED:
            case REACTIVATED:
            case ACTIVATED:
            case STRIKE_OFF_CANCELLED:
            case TC_ISSUED:
            case TC_REISSUED:
            case DUES_PAID:
                return "success";
            case TC_APPROVED:
            case DUES_MANUAL:
            case DUES_WAIVED:
                return "warning";
            case RE_REGISTERED:
            case REGISTRATION_NO_ASSIGNED:
            case SR_ASSIGNED:
            case SR_CHANGED:
                return "primary";
            default:
                return "secondary";
        }
    }

    /** A readable sentence from the stored values. Only the fields each event type really fills are used. */
    static String describe(StudentExecution e) {
        String oldV = e.getOldValue();
        String newV = e.getNewValue();
        String remark = e.getRemark();
        StringBuilder sb = new StringBuilder();
        switch (e.getEventType()) {
            case STRUCK_OFF:
                sb.append("Last date at school ").append(e.getEventDate().format(DATE));
                if (e.getExitReason() != null) {
                    sb.append(". Reason: ").append(e.getExitReason().getLabel());
                }
                break;
            case DUES_MANUAL:
                sb.append("Dues entered by hand: ₹ ").append(nz(newV));
                if (oldV != null) {
                    sb.append(" (estimate was ₹ ").append(oldV).append(")");
                }
                break;
            case TC_APPROVED:
                sb.append("Dues fixed at ₹ ").append(nz(newV));
                break;
            case DUES_PAID:
                sb.append("₹ ").append(nz(newV)).append(" paid");
                break;
            case DUES_WAIVED:
                sb.append("₹ ").append(nz(newV)).append(" waived");
                break;
            case DUES_RECEIPT_VOIDED:
                sb.append("Receipt ").append(nz(oldV)).append(" voided");
                break;
            case TC_ISSUED:
                sb.append("TC number ").append(nz(newV));
                break;
            case TC_REISSUED:
                sb.append("TC ").append(nz(oldV)).append(" replaced by TC ").append(nz(newV));
                break;
            case REACTIVATED:
            case STRIKE_OFF_CANCELLED:
                sb.append("Back to active");
                break;
            default:
                break;
        }
        if (remark != null) {
            if (sb.length() > 0) {
                sb.append(". ");
            }
            sb.append(remark);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String nz(String v) {
        return v == null ? "—" : v;
    }
}
