package com.smsweb.sms.services.student;

import com.smsweb.sms.dto.RegionalImportRowDto;
import com.smsweb.sms.dto.RegionalStudentSearchResultDto;
import com.smsweb.sms.helper.ExcelFileHandler;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.student.StudentRegionalDetail;
import com.smsweb.sms.repositories.student.StudentRegionalDetailRepository;
import com.smsweb.sms.repositories.student.StudentRepository;
import com.smsweb.sms.services.users.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StudentRegionalDetailService {

    private static final Logger log = LoggerFactory.getLogger(StudentRegionalDetailService.class);

    private final StudentService studentService;
    private final StudentRepository studentRepository;
    private final StudentRegionalDetailRepository regionalDetailRepository;
    private final ExcelFileHandler excelFileHandler;
    private final UserService userService;
    private final AcademicStudentService academicStudentService;

    public StudentRegionalDetailService(StudentService studentService,
                                        StudentRepository studentRepository,
                                        StudentRegionalDetailRepository regionalDetailRepository,
                                        ExcelFileHandler excelFileHandler,
                                        UserService userService,
                                        AcademicStudentService academicStudentService) {
        this.studentService = studentService;
        this.studentRepository = studentRepository;
        this.regionalDetailRepository = regionalDetailRepository;
        this.excelFileHandler = excelFileHandler;
        this.userService = userService;
        this.academicStudentService = academicStudentService;
    }

    /**
     * Live search for the "Single Student Update" mode — reuses the same school +
     * academic-year scoped search already used by the Fee Submission form, so behaviour
     * (matching on name/father name/mother name/SR no, 10-per-page) is consistent app-wide.
     */
    public List<RegionalStudentSearchResultDto> searchStudents(String query, Long academicYearId, Long schoolId, int page) {
        log.info("Inside searchStudents — query={}, page={}", query, page);
        List<AcademicStudent> matches = academicStudentService.searchStudents(query, academicYearId, schoolId, page);
        List<RegionalStudentSearchResultDto> results = new ArrayList<>();
        if (matches == null) return results;

        List<Long> studentIds = matches.stream()
                .map(as -> as.getStudent().getId())
                .collect(Collectors.toList());
        Map<Long, StudentRegionalDetail> existingByStudentId = regionalDetailRepository
                .findAllByStudent_IdIn(studentIds).stream()
                .collect(Collectors.toMap(rd -> rd.getStudent().getId(), rd -> rd));

        for (AcademicStudent as : matches) {
            Student student = as.getStudent();
            RegionalStudentSearchResultDto dto = new RegionalStudentSearchResultDto();
            dto.setStudentUuid(student.getUuid() != null ? student.getUuid().toString() : null);
            dto.setStudentName(student.getStudentName());
            dto.setFatherName(student.getFatherName());
            dto.setMotherName(student.getMotherName());
            dto.setAddress(student.getAddress());
            dto.setClassSrNo(as.getClassSrNo());
            dto.setGradeName(as.getGrade() != null ? as.getGrade().getGradeName() : null);
            dto.setSectionName(as.getSection() != null ? as.getSection().getSectionName() : null);

            StudentRegionalDetail existing = existingByStudentId.get(student.getId());
            if (existing != null) {
                dto.setStudentNameRegional(existing.getStudentNameRegional());
                dto.setFatherNameRegional(existing.getFatherNameRegional());
                dto.setMotherNameRegional(existing.getMotherNameRegional());
                dto.setAddressRegional(existing.getAddressRegional());
            }
            results.add(dto);
        }
        return results;
    }

    /** Insert/update a single student's regional details — the "Single Student Update" save action. */
    public Map<String, Object> saveOne(RegionalImportRowDto row, Long schoolId) {
        log.info("Inside saveOne — uuid={}", row != null ? row.getStudentUuid() : null);
        Map<String, Integer> summary = saveConfirmed(row == null ? List.of() : List.of(row), schoolId);
        Map<String, Object> result = new HashMap<>(summary);
        result.put("success", summary.getOrDefault("saved", 0) > 0);
        return result;
    }

    /** Builds the downloadable template — every active student of the school, pre-filled with any existing regional values. */
    public ByteArrayInputStream buildTemplate(Long schoolId, String schoolName, String sessionFormat) throws IOException {
        log.info("Inside buildTemplate — schoolId={}", schoolId);
        List<Student> students = studentService.getAllActiveStudentsOfSchool(schoolId);
        List<Long> studentIds = students.stream().map(Student::getId).collect(Collectors.toList());
        Map<Long, StudentRegionalDetail> existingByStudentId = regionalDetailRepository
                .findAllByStudent_IdIn(studentIds).stream()
                .collect(Collectors.toMap(rd -> rd.getStudent().getId(), rd -> rd));
        return excelFileHandler.buildStudentRegionalTemplate(students, existingByStudentId, schoolName, sessionFormat);
    }

    /**
     * Parses the uploaded workbook and matches every row to a student by UUID (scoped to
     * this school + must be Active). Only used for preview — nothing is saved here.
     */
    public List<RegionalImportRowDto> parsePreview(MultipartFile file, Long schoolId) throws IOException {
        log.info("Inside parsePreview — schoolId={}", schoolId);
        List<String[]> rawRows = excelFileHandler.parseStudentRegionalUpload(file.getInputStream());
        List<RegionalImportRowDto> result = new ArrayList<>();
        int sno = 1;
        for (String[] raw : rawRows) {
            RegionalImportRowDto dto = new RegionalImportRowDto();
            dto.setSno(sno++);
            String uuidStr = raw[0];
            dto.setStudentUuid(uuidStr);
            dto.setStudentNameRegional(raw[1]);
            dto.setFatherNameRegional(raw[2]);
            dto.setMotherNameRegional(raw[3]);
            dto.setAddressRegional(raw[4]);
            boolean hasFormulaArtifact = raw.length > 5 && "true".equals(raw[5]);
            dto.setHasWarning(hasFormulaArtifact);
            if (hasFormulaArtifact) {
                dto.setMessage("One or more regional value(s) looked like an unconverted spreadsheet formula "
                        + "(e.g. a Google Sheets =GOOGLETRANSLATE(...) that wasn't 'pasted as values' before "
                        + "downloading) and were cleared. Please retype these before saving.");
            }

            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (Exception e) {
                dto.setMatchStatus("NOT_FOUND");
                dto.setMessage("Invalid/unreadable identifier — row skipped.");
                result.add(dto);
                continue;
            }

            Optional<Student> activeMatch = studentRepository.findByUuidAndStatusAndSchool_Id(uuid, "Active", schoolId);
            if (activeMatch.isPresent()) {
                Student student = activeMatch.get();
                dto.setStudentName(student.getStudentName());
                dto.setFatherName(student.getFatherName());
                dto.setMotherName(student.getMotherName());
                dto.setAddress(student.getAddress());
                dto.setMatchStatus("MATCHED");
            } else {
                // Give a precise reason without leaking data across schools
                Optional<Student> anyMatch = studentRepository.findByUuid(uuid);
                if (anyMatch.isEmpty()) {
                    dto.setMatchStatus("NOT_FOUND");
                    dto.setMessage("Student not found — this row will be skipped.");
                } else {
                    dto.setMatchStatus("INACTIVE");
                    dto.setMessage("Student is inactive or belongs to a different school — this row will be skipped.");
                }
            }
            result.add(dto);
        }
        return result;
    }

    /** Saves only the MATCHED rows the admin confirmed. Returns {saved, skipped}. */
    public Map<String, Integer> saveConfirmed(List<RegionalImportRowDto> rows, Long schoolId) {
        log.info("Inside saveConfirmed — schoolId={}, rows={}", schoolId, rows == null ? 0 : rows.size());
        int saved = 0;
        int skipped = 0;
        UserEntity currentUser = null;
        try {
            currentUser = userService.getLoggedInUser();
        } catch (Exception e) {
            log.warn("Unable to resolve logged-in user for audit fields", e);
        }

        if (rows != null) {
            for (RegionalImportRowDto row : rows) {
                try {
                    UUID uuid = UUID.fromString(row.getStudentUuid());
                    Optional<Student> studentOpt = studentRepository.findByUuidAndStatusAndSchool_Id(uuid, "Active", schoolId);
                    if (studentOpt.isEmpty()) {
                        skipped++;
                        continue;
                    }
                    Student student = studentOpt.get();
                    StudentRegionalDetail detail = regionalDetailRepository.findByStudent_Id(student.getId())
                            .orElseGet(StudentRegionalDetail::new);
                    detail.setStudent(student);
                    detail.setStudentNameRegional(trimOrNull(row.getStudentNameRegional()));
                    detail.setFatherNameRegional(trimOrNull(row.getFatherNameRegional()));
                    detail.setMotherNameRegional(trimOrNull(row.getMotherNameRegional()));
                    detail.setAddressRegional(trimOrNull(row.getAddressRegional()));
                    if (detail.getId() == null) {
                        detail.setCreatedBy(currentUser);
                    }
                    detail.setUpdatedBy(currentUser);
                    regionalDetailRepository.save(detail);
                    saved++;
                } catch (Exception e) {
                    log.error("Failed to save regional detail row for uuid={}", row.getStudentUuid(), e);
                    skipped++;
                }
            }
        }
        Map<String, Integer> summary = new HashMap<>();
        summary.put("saved", saved);
        summary.put("skipped", skipped);
        return summary;
    }

    private String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
