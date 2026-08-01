package com.smsweb.sms.services.student;

import com.smsweb.sms.models.admin.SystemConfig;
import com.smsweb.sms.repositories.admin.SchoolRepository;
import com.smsweb.sms.repositories.admin.SystemConfigRepository;
import com.smsweb.sms.repositories.student.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Generates the next PSRN (per-school sequential registration number) for a
 * newly registered student.
 *
 * Design:
 *   - system_config holds one static row per school: "SCHOOL_PSRN_<schoolId>"
 *     = the starting value for that school's block (e.g. "1000001" for
 *     school 2). Seeded once at school onboarding; the app never writes back
 *     to this row — it only matters for a school's very first student.
 *   - On every new registration: lock the School row as a per-school mutex,
 *     then read MAX(psrn) for that school. If any student already exists,
 *     use max + 1. If none exist yet (MAX is null), fall back to the static
 *     system_config starting value.
 *   - "Remove Student" is a soft delete (Student.status flips to INACTIVE,
 *     the row and its psrn stay in the table), so MAX(psrn) never regresses
 *     under normal use — see StudentService.deleteStudent().
 *
 * generateNextPsrn() MUST be called from within an existing @Transactional
 * method (StudentService.saveStudent) so the school-row lock acquired here
 * is held for the remainder of that transaction. Without that, two
 * concurrent registrations for the same school could read the same
 * MAX(psrn) before either commits and end up assigning the same value.
 */
@Service
public class PsrnService {

    private static final Logger log = LoggerFactory.getLogger(PsrnService.class);
    private static final String CONFIG_KEY_PREFIX = "SCHOOL_PSRN_";

    private final SchoolRepository schoolRepository;
    private final StudentRepository studentRepository;
    private final SystemConfigRepository systemConfigRepository;

    @Autowired
    public PsrnService(SchoolRepository schoolRepository,
                        StudentRepository studentRepository,
                        SystemConfigRepository systemConfigRepository) {
        this.schoolRepository = schoolRepository;
        this.studentRepository = studentRepository;
        this.systemConfigRepository = systemConfigRepository;
    }

    public Long generateNextPsrn(Long schoolId) {
        log.info("Inside generateNextPsrn - schoolId={}", schoolId);

        // Per-school mutex — held until the enclosing transaction commits or
        // rolls back. Must run before the MAX(psrn) read below so two
        // concurrent registrations for the same school serialize instead of
        // both reading the same max value.
        schoolRepository.findByIdForUpdate(schoolId)
                .orElseThrow(() -> new IllegalStateException("School not found: " + schoolId));

        Long maxPsrn = studentRepository.findMaxPsrnBySchoolId(schoolId);
        if (maxPsrn != null) {
            return maxPsrn + 1;
        }

        // No students yet for this school — use the static starting value.
        String configKey = CONFIG_KEY_PREFIX + schoolId;
        SystemConfig config = systemConfigRepository.findByConfigName(configKey)
                .orElseThrow(() -> new IllegalStateException(
                        "PSRN starting value not configured for school " + schoolId +
                        " (expected system_config row '" + configKey + "'). " +
                        "Seed this value before registering students for this school."));
        try {
            return Long.parseLong(config.getConfigValue().trim());
        } catch (NumberFormatException nfe) {
            throw new IllegalStateException(
                    "PSRN starting value for school " + schoolId + " is not a valid number: '" +
                    config.getConfigValue() + "'");
        }
    }
}
