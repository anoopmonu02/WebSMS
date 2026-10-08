package com.smsweb.sms.services.tc;

import com.smsweb.sms.dto.tc.ClassDivisionRequest;
import com.smsweb.sms.dto.tc.ClassDivisionRow;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.tc.ClassDivision;
import com.smsweb.sms.models.tc.ClassDivisionGrade;
import com.smsweb.sms.models.tc.enums.ExitStatus;
import com.smsweb.sms.models.universal.Grade;
import com.smsweb.sms.repositories.tc.ClassDivisionGradeRepository;
import com.smsweb.sms.repositories.tc.ClassDivisionRepository;
import com.smsweb.sms.repositories.tc.StudentExitRepository;
import com.smsweb.sms.repositories.tc.TransferCertificateRepository;
import com.smsweb.sms.repositories.universal.GradeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Class groups (for example "Primary") and the grades mapped to them. Each group owns one TC
 * register: a TC format and a running TC number. A grade belongs to exactly one group per school.
 *
 * <p>User-facing problems are thrown as {@link TcValidationException}. All methods take the school
 * from the caller (the session), never from request data.</p>
 */
@Service
public class ClassDivisionService {

    private static final Logger log = LoggerFactory.getLogger(ClassDivisionService.class);

    public static final String STATUS_ACTIVE = "Active";
    public static final String STATUS_INACTIVE = "Inactive";

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_DESCRIPTION_LENGTH = 255;
    private static final long MAX_START_NUMBER = 99_999_999L;

    private final ClassDivisionRepository classDivisionRepository;
    private final ClassDivisionGradeRepository classDivisionGradeRepository;
    private final TransferCertificateRepository transferCertificateRepository;
    private final StudentExitRepository studentExitRepository;
    private final GradeRepository gradeRepository;
    private final TcConfigService tcConfigService;

    public ClassDivisionService(ClassDivisionRepository classDivisionRepository,
                                ClassDivisionGradeRepository classDivisionGradeRepository,
                                TransferCertificateRepository transferCertificateRepository,
                                StudentExitRepository studentExitRepository,
                                GradeRepository gradeRepository,
                                TcConfigService tcConfigService) {
        this.classDivisionRepository = classDivisionRepository;
        this.classDivisionGradeRepository = classDivisionGradeRepository;
        this.transferCertificateRepository = transferCertificateRepository;
        this.studentExitRepository = studentExitRepository;
        this.gradeRepository = gradeRepository;
        this.tcConfigService = tcConfigService;
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    /** Rows for the Class Groups page: each group with its grades and the grades it can still take. */
    @Transactional(readOnly = true)
    public List<ClassDivisionRow> listRows(School school) {
        log.info("Inside listRows");
        Long schoolId = school.getId();

        List<ClassDivision> divisions = classDivisionRepository.findBySchool_IdOrderByNameAsc(schoolId);
        List<ClassDivisionGrade> mappings = classDivisionGradeRepository.findAllWithGradeAndDivision(schoolId);
        List<Grade> allGrades = new ArrayList<>(gradeRepository.findAll());
        allGrades.sort(Comparator.comparing(Grade::getId));

        Map<Long, Set<Long>> gradeIdsByDivision = new HashMap<>();
        Set<Long> mappedGradeIds = new HashSet<>();
        for (ClassDivisionGrade m : mappings) {
            gradeIdsByDivision.computeIfAbsent(m.getDivision().getId(), k -> new LinkedHashSet<>())
                    .add(m.getGrade().getId());
            mappedGradeIds.add(m.getGrade().getId());
        }

        List<ClassDivisionRow> rows = new ArrayList<>();
        for (ClassDivision d : divisions) {
            ClassDivisionRow row = new ClassDivisionRow();
            row.setUuid(d.getUuid());
            row.setName(d.getName());
            row.setDescription(d.getDescription());
            row.setTcFormat(d.getTcFormat());
            row.setStartNumber(d.getStartNumber());
            row.setNextNumber(d.getNextNumber());
            row.setStatus(d.getStatus());
            row.setStartLocked(transferCertificateRepository.existsByDivision_Id(d.getId()));

            Set<Long> own = gradeIdsByDivision.getOrDefault(d.getId(), Set.of());
            for (Grade g : allGrades) {
                boolean selected = own.contains(g.getId());
                if (selected) {
                    row.getGradeIds().add(g.getId());
                    row.getGradeNames().add(g.getGradeName());
                }
                if (selected || !mappedGradeIds.contains(g.getId())) {
                    row.getGradeOptions().add(new ClassDivisionRow.GradeOption(g.getId(), g.getGradeName(), selected));
                }
            }
            rows.add(row);
        }
        return rows;
    }

    /** The class group a grade belongs to, with the group loaded so it is usable outside a transaction. */
    @Transactional(readOnly = true)
    public Optional<ClassDivision> findForGrade(Long schoolId, Long gradeId) {
        return classDivisionGradeRepository.findWithDivision(schoolId, gradeId).map(ClassDivisionGrade::getDivision);
    }

    // ── Writes ───────────────────────────────────────────────────────────────

    @Transactional
    public ClassDivision create(ClassDivisionRequest req, School school, UserEntity user) {
        log.info("Inside create class division");
        Long schoolId = school.getId();

        String name = cleanName(req.getName());
        String description = cleanDescription(req.getDescription());
        String tcFormat = cleanFormat(req.getTcFormat(), school, null);
        long start = cleanStart(req.getStartNumber());
        String status = cleanStatus(req.getStatus(), STATUS_ACTIVE);

        if (classDivisionRepository.existsBySchool_IdAndNameIgnoreCase(schoolId, name)) {
            throw new TcValidationException("A class group named '" + name + "' already exists.");
        }

        ClassDivision division = new ClassDivision();
        division.setSchool(school);
        division.setName(name);
        division.setDescription(description);
        division.setTcFormat(tcFormat);
        division.setStartNumber(start);
        division.setNextNumber(start);
        division.setStatus(status);
        division.setCreatedBy(user);
        try {
            return classDivisionRepository.saveAndFlush(division);
        } catch (DataIntegrityViolationException e) {
            // Two admins saved the same name at the same moment (unique key school + name).
            throw new TcValidationException("A class group named '" + name + "' already exists.");
        }
    }

    @Transactional
    public ClassDivision update(UUID uuid, ClassDivisionRequest req, School school, UserEntity user) {
        log.info("Inside update class division");
        Long schoolId = school.getId();

        ClassDivision division = lockDivision(uuid, schoolId);

        String name = cleanName(req.getName());
        String description = cleanDescription(req.getDescription());
        String tcFormat = cleanFormat(req.getTcFormat(), school, division.getTcFormat());
        long start = cleanStart(req.getStartNumber());
        String status = cleanStatus(req.getStatus(), division.getStatus());

        if (!name.equalsIgnoreCase(division.getName())
                && classDivisionRepository.existsBySchool_IdAndNameIgnoreCase(schoolId, name)) {
            throw new TcValidationException("A class group named '" + name + "' already exists.");
        }
        if (start != division.getStartNumber()) {
            if (transferCertificateRepository.existsByDivision_Id(division.getId())) {
                throw new TcValidationException(
                        "The start number cannot be changed because a TC was already issued from this group.");
            }
            division.setStartNumber(start);
            division.setNextNumber(start);
        }

        division.setName(name);
        division.setDescription(description);
        division.setTcFormat(tcFormat);
        division.setStatus(status);
        division.setUpdatedBy(user);
        try {
            return classDivisionRepository.saveAndFlush(division);
        } catch (DataIntegrityViolationException e) {
            throw new TcValidationException("A class group named '" + name + "' already exists.");
        }
    }

    /**
     * Replaces the grades of a group. A grade can be in one group only, and a grade cannot be taken
     * out while a student of that grade has an open strike-off (the TC needs the group).
     */
    @Transactional
    public void saveGradeMapping(UUID divisionUuid, List<Long> gradeIds, School school, UserEntity user) {
        log.info("Inside saveGradeMapping");
        Long schoolId = school.getId();

        ClassDivision division = lockDivision(divisionUuid, schoolId);

        Set<Long> wanted = new LinkedHashSet<>();
        if (gradeIds != null) {
            for (Long id : gradeIds) {
                if (id != null) {
                    wanted.add(id);
                }
            }
        }

        Map<Long, Grade> gradesById = new HashMap<>();
        for (Grade g : gradeRepository.findAllById(wanted)) {
            gradesById.put(g.getId(), g);
        }
        if (gradesById.size() != wanted.size()) {
            throw new TcValidationException("One of the selected grades does not exist.");
        }

        List<ClassDivisionGrade> schoolMappings = classDivisionGradeRepository.findAllWithGradeAndDivision(schoolId);
        Map<Long, ClassDivisionGrade> mappingByGrade = new HashMap<>();
        for (ClassDivisionGrade m : schoolMappings) {
            mappingByGrade.put(m.getGrade().getId(), m);
        }

        // A grade already in another group cannot be added here.
        for (Long gradeId : wanted) {
            ClassDivisionGrade existing = mappingByGrade.get(gradeId);
            if (existing != null && !existing.getDivision().getId().equals(division.getId())) {
                throw new TcValidationException("Grade '" + gradesById.get(gradeId).getGradeName()
                        + "' is already in the group '" + existing.getDivision().getName() + "'.");
            }
        }

        // Grades to take out of this group.
        List<ClassDivisionGrade> toRemove = new ArrayList<>();
        for (ClassDivisionGrade m : schoolMappings) {
            if (m.getDivision().getId().equals(division.getId()) && !wanted.contains(m.getGrade().getId())) {
                if (studentExitRepository.existsBySchool_IdAndGrade_IdAndStatusIn(schoolId, m.getGrade().getId(),
                        EnumSet.of(ExitStatus.EXITED, ExitStatus.APPROVED))) {
                    throw new TcValidationException("Grade '" + m.getGrade().getGradeName()
                            + "' cannot be removed: a student of this grade has a strike-off that is not finished yet.");
                }
                toRemove.add(m);
            }
        }

        // Grades to add.
        List<ClassDivisionGrade> toAdd = new ArrayList<>();
        for (Long gradeId : wanted) {
            if (!mappingByGrade.containsKey(gradeId)) {
                ClassDivisionGrade m = new ClassDivisionGrade();
                m.setSchool(school);
                m.setDivision(division);
                m.setGrade(gradesById.get(gradeId));
                m.setCreatedBy(user);
                toAdd.add(m);
            }
        }

        try {
            classDivisionGradeRepository.deleteAll(toRemove);
            classDivisionGradeRepository.flush();
            classDivisionGradeRepository.saveAll(toAdd);
            classDivisionGradeRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // Another admin mapped the same grade at the same moment (unique key school + grade).
            throw new TcValidationException("A grade was just added to another group. Reload the page and try again.");
        }
    }

    // ── Validation helpers ───────────────────────────────────────────────────

    private ClassDivision lockDivision(UUID uuid, Long schoolId) {
        if (uuid == null) {
            throw new TcValidationException("Class group not found.");
        }
        ClassDivision found = classDivisionRepository.findByUuidAndSchool_Id(uuid, schoolId)
                .orElseThrow(() -> new TcValidationException("Class group not found."));
        // Lock the row (last in the lock order) so a TC being issued cannot interleave with an edit.
        return classDivisionRepository.findByIdForUpdate(found.getId(), schoolId)
                .orElseThrow(() -> new TcValidationException("Class group not found."));
    }

    private static String cleanName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw new TcValidationException("Group name is required.");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new TcValidationException("Group name is too long (maximum " + MAX_NAME_LENGTH + " characters).");
        }
        return name;
    }

    private static String cleanDescription(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > MAX_DESCRIPTION_LENGTH) {
            throw new TcValidationException("Description is too long (maximum " + MAX_DESCRIPTION_LENGTH + " characters).");
        }
        return text;
    }

    /** The format must be one of the school's configured names (an unchanged legacy value is tolerated). */
    private String cleanFormat(String raw, School school, String currentValue) {
        String format = raw == null ? "" : raw.trim().toUpperCase();
        if (format.isEmpty()) {
            throw new TcValidationException("TC format is required.");
        }
        if (!tcConfigService.formatNames(school).contains(format) && !format.equals(currentValue)) {
            throw new TcValidationException("TC format '" + format + "' is not in the list of formats for this school.");
        }
        return format;
    }

    private static long cleanStart(Long raw) {
        if (raw == null || raw < 1 || raw > MAX_START_NUMBER) {
            throw new TcValidationException("Start number must be between 1 and " + MAX_START_NUMBER + ".");
        }
        return raw;
    }

    private static String cleanStatus(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String status = raw.trim();
        if (STATUS_ACTIVE.equalsIgnoreCase(status)) {
            return STATUS_ACTIVE;
        }
        if (STATUS_INACTIVE.equalsIgnoreCase(status)) {
            return STATUS_INACTIVE;
        }
        throw new TcValidationException("Status must be Active or Inactive.");
    }
}
