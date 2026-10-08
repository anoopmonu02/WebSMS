package com.smsweb.sms.services.tc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smsweb.sms.dto.tc.DuesPanelDto;
import com.smsweb.sms.dto.tc.IssueTcRequest;
import com.smsweb.sms.dto.tc.ReissueRequest;
import com.smsweb.sms.dto.tc.TcFilter;
import com.smsweb.sms.dto.tc.TcPanelDto;
import com.smsweb.sms.dto.tc.TcPrintDto;
import com.smsweb.sms.dto.tc.TcRowDto;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.student.StudentRegionalDetail;
import com.smsweb.sms.models.tc.ClassDivision;
import com.smsweb.sms.models.tc.ClassDivisionGrade;
import com.smsweb.sms.models.tc.StudentExit;
import com.smsweb.sms.models.tc.TransferCertificate;
import com.smsweb.sms.models.tc.enums.EventSource;
import com.smsweb.sms.models.tc.enums.EventType;
import com.smsweb.sms.models.tc.enums.ExitStatus;
import com.smsweb.sms.models.tc.enums.RefType;
import com.smsweb.sms.models.tc.enums.TcStatus;
import com.smsweb.sms.repositories.student.StudentRegionalDetailRepository;
import com.smsweb.sms.repositories.tc.ClassDivisionGradeRepository;
import com.smsweb.sms.repositories.tc.ClassDivisionRepository;
import com.smsweb.sms.repositories.tc.StudentExitRepository;
import com.smsweb.sms.repositories.tc.TransferCertificateRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Issuing and re-issuing the Transfer Certificate, the TC panel of the exit page, the TC register
 * and the print data.
 *
 * <p>A TC row exists only after the student collected the TC. Issue needs an APPROVED exit with the
 * dues frozen and fully cleared. The TC number comes from the class group (division) of the
 * student's last grade, taken under that row's lock, so numbers are gap free per register. Lock
 * order: exit row, then division row. A re-issue never edits the old TC: it marks it SUPERSEDED and
 * writes a new row with a new number from the same register.</p>
 *
 * <p>The old {@code Student.tcNo} / {@code removalCause} columns are deliberately not touched.</p>
 */
@Service
public class TcService {

    private static final Logger log = LoggerFactory.getLogger(TcService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> RELATIONS = Set.of("PARENT", "GUARDIAN", "STUDENT");
    private static final int MIN_REISSUE_REASON = 10;
    private static final int MAX_REISSUE_REASON = 500;
    private static final int MAX_NAME = 150;

    private final TransferCertificateRepository tcRepository;
    private final StudentExitRepository studentExitRepository;
    private final ClassDivisionRepository classDivisionRepository;
    private final ClassDivisionGradeRepository classDivisionGradeRepository;
    private final DueClearanceReceiptService receiptService;
    private final StudentEventService studentEventService;
    private final StudentRegionalDetailRepository regionalDetailRepository;

    public TcService(TransferCertificateRepository tcRepository,
                     StudentExitRepository studentExitRepository,
                     ClassDivisionRepository classDivisionRepository,
                     ClassDivisionGradeRepository classDivisionGradeRepository,
                     DueClearanceReceiptService receiptService,
                     StudentEventService studentEventService,
                     StudentRegionalDetailRepository regionalDetailRepository) {
        this.tcRepository = tcRepository;
        this.studentExitRepository = studentExitRepository;
        this.classDivisionRepository = classDivisionRepository;
        this.classDivisionGradeRepository = classDivisionGradeRepository;
        this.receiptService = receiptService;
        this.studentEventService = studentEventService;
        this.regionalDetailRepository = regionalDetailRepository;
    }

    // ───────────────────────────── panel ─────────────────────────────

    @Transactional(readOnly = true)
    public TcPanelDto panel(UUID exitUuid, School school, AcademicYear currentYear) {
        StudentExit exit = studentExitRepository.findByUuidAndSchool_Id(exitUuid, school.getId())
                .orElseThrow(() -> new TcValidationException("This exit record was not found."));
        TcPanelDto p = new TcPanelDto();
        List<TransferCertificate> all = tcRepository.findByExit_IdOrderByIdAsc(exit.getId());
        p.setTcs(all.stream().map(this::toRow).collect(Collectors.toList()));
        boolean hasValid = all.stream().anyMatch(t -> t.getStatus() == TcStatus.VALID);
        p.setCanReissue(hasValid);

        ClassDivisionGrade mapping = classDivisionGradeRepository
                .findWithDivision(school.getId(), exit.getGrade().getId()).orElse(null);
        if (mapping != null) {
            p.setDivisionName(mapping.getDivision().getName());
            p.setTcFormat(mapping.getDivision().getTcFormat());
            p.setNextNumber(mapping.getDivision().getNextNumber());
        }

        if (hasValid || exit.getStatus() == ExitStatus.TC_COLLECTED) {
            return p;
        }
        switch (exit.getStatus()) {
            case EXITED:
                p.setBlockReason("Waiting for admin approval.");
                return p;
            case APPROVED:
                break;
            default:
                p.setBlockReason("No TC can be issued: this exit is " + exit.getStatus().getLabel().toLowerCase() + ".");
                return p;
        }
        DuesPanelDto dues = receiptService.buildPanel(exit, currentYear);
        if (!dues.isCleared()) {
            p.setBlockReason("Dues are not cleared yet.");
            return p;
        }
        if (mapping == null) {
            p.setBlockReason("Class " + exit.getGrade().getGradeName()
                    + " is not in any class group. Add it under TC Class Groups first.");
            return p;
        }
        if (!"Active".equalsIgnoreCase(mapping.getDivision().getStatus())) {
            p.setBlockReason("Class group " + mapping.getDivision().getName() + " is inactive.");
            return p;
        }
        p.setCanIssue(true);
        p.setRegionalMissing(regionalOf(exit.getStudent()) == null);
        return p;
    }

    // ───────────────────────────── issue ─────────────────────────────

    /** Issues the TC (the student collected it). Posting twice cannot create two TCs: the second call is refused. */
    @Transactional
    public TransferCertificate issue(UUID exitUuid, IssueTcRequest req, School school,
                                     AcademicYear currentYear, UserEntity user) {
        log.info("Inside issue TC: exit={}", exitUuid);
        requireContext(school, currentYear, user);
        if (req == null) {
            throw new TcValidationException("Enter who collected the TC.");
        }
        String collector = collectorName(req.getCollectedByName());
        String relation = collectorRelation(req.getCollectedByRelation());

        StudentExit exit = lockExit(exitUuid, school);
        if (tcRepository.findFirstByExit_IdAndStatus(exit.getId(), TcStatus.VALID).isPresent()
                || exit.getStatus() == ExitStatus.TC_COLLECTED) {
            throw new InvalidExitStateException("A TC was already issued for this exit. Use re-issue if a new copy is needed.");
        }
        if (exit.getStatus() != ExitStatus.APPROVED || !exit.isDuesFrozen()) {
            throw new InvalidExitStateException("A TC can be issued only after the admin has approved the exit.");
        }
        DuesPanelDto dues = receiptService.buildPanel(exit, currentYear);
        if (!dues.isCleared()) {
            throw new InvalidExitStateException("Dues are not cleared yet.");
        }

        ClassDivisionGrade mapping = classDivisionGradeRepository
                .findWithDivision(school.getId(), exit.getGrade().getId())
                .orElseThrow(() -> new TcValidationException("Class " + exit.getGrade().getGradeName()
                        + " is not in any class group. Add it under TC Class Groups first."));
        ClassDivision division = classDivisionRepository
                .findByIdForUpdate(mapping.getDivision().getId(), school.getId())
                .orElseThrow(() -> new TcValidationException("The class group was not found."));
        if (!"Active".equalsIgnoreCase(division.getStatus())) {
            throw new TcValidationException("Class group " + division.getName() + " is inactive.");
        }

        long number = nextNumber(division);
        LocalDate today = LocalDate.now();

        TransferCertificate tc = new TransferCertificate();
        tc.setSchool(school);
        tc.setExit(exit);
        tc.setStudent(exit.getStudent());
        tc.setAcademicStudent(exit.getAcademicStudent());
        tc.setDivision(division);
        tc.setTcNumber(number);
        tc.setTcFormat(division.getTcFormat());
        tc.setIssueDate(today);
        tc.setCollectedByName(collector);
        tc.setCollectedByRelation(relation);
        tc.setReissueNo(1);
        tc.setStatus(TcStatus.VALID);
        tc.setTcData(toJson(snapshot(exit)));
        tc.setCreatedBy(user);
        tc = tcRepository.saveAndFlush(tc);

        exit.setStatus(ExitStatus.TC_COLLECTED);
        exit.setUpdatedBy(user);
        studentExitRepository.save(exit);

        studentEventService.log(EventType.TC_ISSUED, exit.getStudent(), exit.getAcademicStudent(), currentYear,
                today, null, String.valueOf(number), division.getName() + " register, collected by " + collector
                        + " (" + relation + ")", RefType.TC, tc.getId(), EventSource.WEB, user);
        return tc;
    }

    // ───────────────────────────── re-issue ─────────────────────────────

    /** Replaces a TC (lost copy, correction): the old row becomes SUPERSEDED and a new number is taken. */
    @Transactional
    public TransferCertificate reissue(UUID tcUuid, ReissueRequest req, School school,
                                       AcademicYear currentYear, UserEntity user) {
        log.info("Inside reissue TC: tc={}", tcUuid);
        requireContext(school, currentYear, user);
        if (req == null) {
            throw new TcValidationException("Enter the re-issue details.");
        }
        String reason = reissueReason(req.getReason());
        String collector = collectorName(req.getCollectedByName());
        String relation = collectorRelation(req.getCollectedByRelation());

        UUID exitUuid = tcRepository.findExitUuid(tcUuid, school.getId())
                .orElseThrow(() -> new TcValidationException("This TC was not found."));
        StudentExit exit = lockExit(exitUuid, school);
        if (exit.getStatus() == ExitStatus.REACTIVATED) {
            throw new InvalidExitStateException("This student was reactivated, so the TC cannot be re-issued.");
        }
        // Read the TC only after the exit lock, so a concurrent re-issue is seen.
        TransferCertificate old = tcRepository.findByUuidAndSchool_Id(tcUuid, school.getId())
                .orElseThrow(() -> new TcValidationException("This TC was not found."));
        if (old.getStatus() != TcStatus.VALID) {
            throw new InvalidExitStateException("This TC was already replaced by a newer one.");
        }

        ClassDivision division = classDivisionRepository
                .findByIdForUpdate(old.getDivision().getId(), school.getId())
                .orElseThrow(() -> new TcValidationException("The class group was not found."));
        long number = nextNumber(division);
        LocalDate today = LocalDate.now();

        old.setStatus(TcStatus.SUPERSEDED);
        old.setUpdatedBy(user);
        tcRepository.saveAndFlush(old);

        TransferCertificate tc = new TransferCertificate();
        tc.setSchool(school);
        tc.setExit(exit);
        tc.setStudent(old.getStudent());
        tc.setAcademicStudent(old.getAcademicStudent());
        tc.setDivision(division);
        tc.setTcNumber(number);
        tc.setTcFormat(old.getTcFormat());
        tc.setIssueDate(today);
        tc.setCollectedByName(collector);
        tc.setCollectedByRelation(relation);
        tc.setReissueNo(old.getReissueNo() + 1);
        tc.setPreviousTc(old);
        tc.setCause(reason);
        tc.setStatus(TcStatus.VALID);
        tc.setTcData(old.getTcData());
        tc.setCreatedBy(user);
        tc = tcRepository.saveAndFlush(tc);

        studentEventService.log(EventType.TC_REISSUED, old.getStudent(), old.getAcademicStudent(), currentYear,
                today, String.valueOf(old.getTcNumber()), String.valueOf(number), reason,
                RefType.TC, tc.getId(), EventSource.WEB, user);
        return tc;
    }

    // ───────────────────────────── register and print ─────────────────────────────

    @Transactional(readOnly = true)
    public Page<TcRowDto> search(TcFilter filter, School school) {
        TcFilter f = filter == null ? new TcFilter() : filter;
        int size = Math.min(Math.max(f.getSize(), 1), 100);
        Pageable pageable = PageRequest.of(Math.max(f.getPage(), 0), size, Sort.by(Sort.Direction.DESC, "id"));
        return tcRepository.findAll(registerSpec(f, school.getId()), pageable).map(this::toRow);
    }

    @Transactional(readOnly = true)
    public TcPrintDto printData(UUID tcUuid, School school) {
        TransferCertificate tc = tcRepository.findByUuidAndSchool_Id(tcUuid, school.getId())
                .orElseThrow(() -> new TcValidationException("This TC was not found."));
        School sc = tc.getSchool();
        TcPrintDto d = new TcPrintDto();
        d.setSchoolName(sc.getSchoolName());
        d.setSchoolAddress(joinNonBlank(" - ", sc.getAddress(), sc.getPincode()));
        d.setSchoolPhone(sc.getMobile1());
        d.setSchoolBoard(sc.getBoard());
        d.setTcNumber(tc.getTcNumber());
        d.setTcFormat(tc.getTcFormat());
        d.setDivisionName(tc.getDivision().getName());
        d.setIssueDate(tc.getIssueDate());
        d.setReissueNo(tc.getReissueNo());
        d.setSuperseded(tc.getStatus() == TcStatus.SUPERSEDED);
        d.setCause(tc.getCause());
        d.setCollectedByName(tc.getCollectedByName());
        d.setCollectedByRelation(tc.getCollectedByRelation());
        Map<String, Object> values = new LinkedHashMap<>(parseJson(tc.getTcData()));
        // Regional-language text is read live (not frozen at issue), like the birth certificate.
        StudentRegionalDetail regional = regionalOf(tc.getStudent());
        values.put("studentNameRegional", regional == null ? null : blankToNull(regional.getStudentNameRegional()));
        values.put("fatherNameRegional", regional == null ? null : blankToNull(regional.getFatherNameRegional()));
        values.put("motherNameRegional", regional == null ? null : blankToNull(regional.getMotherNameRegional()));
        values.put("addressRegional", regional == null ? null : blankToNull(regional.getAddressRegional()));
        d.setData(values);
        return d;
    }

    // ───────────────────────────── helpers ─────────────────────────────

    /** The saved regional-language details, or null when the student has none (all four values blank counts as none). */
    private StudentRegionalDetail regionalOf(Student student) {
        if (student == null || student.getId() == null) {
            return null;
        }
        StudentRegionalDetail r = regionalDetailRepository.findByStudent_Id(student.getId()).orElse(null);
        if (r == null) {
            return null;
        }
        boolean any = blankToNull(r.getStudentNameRegional()) != null
                || blankToNull(r.getFatherNameRegional()) != null
                || blankToNull(r.getMotherNameRegional()) != null
                || blankToNull(r.getAddressRegional()) != null;
        return any ? r : null;
    }


    private StudentExit lockExit(UUID exitUuid, School school) {
        if (exitUuid == null) {
            throw new TcValidationException("This exit record was not found.");
        }
        return studentExitRepository.findByUuidForUpdate(exitUuid, school.getId())
                .orElseThrow(() -> new TcValidationException("This exit record was not found."));
    }

    /** Takes the current number and moves the counter on. The division row must be locked. */
    private long nextNumber(ClassDivision division) {
        long number = division.getNextNumber() == null ? 1L : division.getNextNumber();
        division.setNextNumber(number + 1);
        classDivisionRepository.saveAndFlush(division);
        return number;
    }

    /** The values printed on the TC, frozen at issue time. */
    private Map<String, Object> snapshot(StudentExit e) {
        Student s = e.getStudent();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("studentName", s.getStudentName());
        m.put("fatherName", s.getFatherName());
        m.put("motherName", s.getMotherName());
        m.put("dob", s.getDob() == null ? null : s.getDob().toString());
        m.put("gender", s.getGender());
        m.put("nationality", s.getNationality());
        m.put("religion", s.getReligion());
        m.put("registrationNo", s.getRegistrationNo());
        m.put("psrn", s.getPsrn() == null ? null : String.valueOf(s.getPsrn()));
        m.put("srNo", e.getAcademicStudent().getClassSrNo());
        m.put("className", e.getGrade().getGradeName());
        m.put("sectionName", e.getSection().getSectionName());
        m.put("mediumName", e.getMedium().getMediumName());
        m.put("session", e.getAcademicYear().getSessionFormat());
        m.put("lastDate", e.getExitDate().toString());
        m.put("reason", e.getExitReason().getLabel());
        return m;
    }

    private TcRowDto toRow(TransferCertificate t) {
        Student s = t.getStudent();
        StudentExit e = t.getExit();
        return new TcRowDto(t.getUuid(), e.getUuid(), t.getTcNumber(), t.getTcFormat(), t.getDivision().getName(),
                s.getStudentName(), s.getFatherName(),
                e.getGrade().getGradeName() + " " + e.getSection().getSectionName(),
                t.getIssueDate(), t.getReissueNo(), t.getStatus(), t.getCollectedByName(),
                t.getCollectedByRelation(), t.getCause());
    }

    @SuppressWarnings("unchecked")
    private static Specification<TransferCertificate> registerSpec(TcFilter f, Long schoolId) {
        return (root, query, cb) -> {
            boolean count = Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType());
            Join<TransferCertificate, Student> student;
            if (count) {
                student = root.join("student");
            } else {
                student = (Join<TransferCertificate, Student>) (Object) root.fetch("student");
                root.fetch("division");
                Join<Object, Object> exit = (Join<Object, Object>) (Object) root.fetch("exit");
                exit.fetch("grade");
                exit.fetch("section");
            }
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("school").get("id"), schoolId));
            if (f.getDivisionId() != null) {
                p.add(cb.equal(root.get("division").get("id"), f.getDivisionId()));
            }
            if (f.getStatus() != null) {
                p.add(cb.equal(root.get("status"), f.getStatus()));
            }
            if (f.getFromDate() != null) {
                p.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("issueDate"), f.getFromDate()));
            }
            if (f.getToDate() != null) {
                p.add(cb.lessThanOrEqualTo(root.<LocalDate>get("issueDate"), f.getToDate()));
            }
            if (f.getNumberFrom() != null) {
                p.add(cb.greaterThanOrEqualTo(root.<Long>get("tcNumber"), f.getNumberFrom()));
            }
            if (f.getNumberTo() != null) {
                p.add(cb.lessThanOrEqualTo(root.<Long>get("tcNumber"), f.getNumberTo()));
            }
            String text = f.getQ() == null ? "" : f.getQ().replace("%", "").replace("_", "").trim().toLowerCase();
            if (!text.isEmpty()) {
                String like = "%" + text + "%";
                p.add(cb.or(
                        cb.like(cb.lower(student.<String>get("studentName")), like),
                        cb.like(cb.lower(student.<String>get("fatherName")), like),
                        cb.like(cb.lower(student.<String>get("motherName")), like)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    private static void requireContext(School school, AcademicYear year, UserEntity user) {
        if (school == null) {
            throw new TcValidationException("No school is selected for this login.");
        }
        if (year == null) {
            throw new TcValidationException("No active session was found.");
        }
        if (user == null) {
            throw new TcValidationException("Your session has expired. Please log in again.");
        }
    }

    private static String collectorName(String value) {
        String name = blankToNull(value);
        if (name == null) {
            throw new TcValidationException("Enter the name of the person who collected the TC.");
        }
        if (name.length() > MAX_NAME) {
            throw new TcValidationException("The collector name is too long (most " + MAX_NAME + " characters).");
        }
        return name;
    }

    private static String collectorRelation(String value) {
        String rel = blankToNull(value);
        if (rel == null || !RELATIONS.contains(rel.toUpperCase())) {
            throw new TcValidationException("Choose who collected the TC: parent, guardian or the student.");
        }
        return rel.toUpperCase();
    }

    private static String reissueReason(String value) {
        String text = blankToNull(value);
        if (text == null || text.length() < MIN_REISSUE_REASON) {
            throw new TcValidationException("Please enter the reason for the re-issue (at least "
                    + MIN_REISSUE_REASON + " characters).");
        }
        if (text.length() > MAX_REISSUE_REASON) {
            throw new TcValidationException("The reason is too long (most " + MAX_REISSUE_REASON + " characters).");
        }
        return text;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static String joinNonBlank(String separator, String... parts) {
        List<String> kept = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                kept.add(part.trim());
            }
        }
        return String.join(separator, kept);
    }

    private static String toJson(Map<String, Object> map) {
        try {
            return MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not save the TC values", e);
        }
    }

    private static Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception e) {
            log.warn("Could not read stored TC values", e);
            return Collections.emptyMap();
        }
    }
}
