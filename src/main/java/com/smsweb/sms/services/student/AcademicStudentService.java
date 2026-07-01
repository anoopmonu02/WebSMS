package com.smsweb.sms.services.student;

import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.FamilyAccount;
import com.smsweb.sms.models.universal.Grade;
import com.smsweb.sms.models.universal.Medium;
import com.smsweb.sms.models.universal.Section;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.repositories.universal.GradeRepository;
import com.smsweb.sms.repositories.universal.MediumRepository;
import com.smsweb.sms.repositories.universal.SectionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
public class AcademicStudentService {
    private static final Logger log = LoggerFactory.getLogger(AcademicStudentService.class);


    private final AcademicStudentRepository academicStudentRepository;
    private final MediumRepository mediumRepository;
    private final GradeRepository gradeRepository;
    private final SectionRepository sectionRepository;

    public AcademicStudentService(AcademicStudentRepository academicStudentRepository,
                                   MediumRepository mediumRepository,
                                   GradeRepository gradeRepository,
                                   SectionRepository sectionRepository) {
        this.academicStudentRepository = academicStudentRepository;
        this.mediumRepository = mediumRepository;
        this.gradeRepository = gradeRepository;
        this.sectionRepository = sectionRepository;
    }

    // ── Existing methods (unchanged) ──────────────────────────────────────────

    public List<AcademicStudent> searchStudents(String stuname, Long academicYear, Long school) {
        log.info("Inside searchStudents");
        return searchStudents(stuname, academicYear, school, 0);
    }

    public List<AcademicStudent> searchStudents(String stuname, Long academicYear, Long school, int page) {
        log.info("Inside searchStudents");
        Pageable pageable = PageRequest.of(page, 10);
        return academicStudentRepository
                .findAllByAcademicYearAndSchoolAndStudentName(academicYear, school, stuname, pageable)
                .getContent();
    }

    public List<AcademicStudent> searchStudentsFromAllBranches(String stuname, Long academicYear) {
        log.info("Inside searchStudentsFromAllBranches");
        return searchStudentsFromAllBranches(stuname, academicYear, 0);
    }

    public List<AcademicStudent> searchStudentsFromAllBranches(String stuname, Long academicYear, int page) {
        log.info("Inside searchStudentsFromAllBranches (paginated) — deprecated, use sessionFormat variant");
        Pageable pageable = PageRequest.of(page, 10);
        return academicStudentRepository
                .findAllByAcademicYearAndStudentName(academicYear, stuname, pageable)
                .getContent();
    }

    // Cross-branch search by sessionFormat (e.g. "2026-2027") — finds students across all schools in the same academic year
    public List<AcademicStudent> searchStudentsFromAllBranches(String stuname, String sessionFormat, int page) {
        log.info("Inside searchStudentsFromAllBranches by sessionFormat (paginated)");
        Pageable pageable = PageRequest.of(page, 10);
        return academicStudentRepository
                .findAllBySessionFormatAndStudentName(sessionFormat, stuname, pageable)
                .getContent();
    }

    public AcademicStudent searchStudentByIdCrossBranch(Long academicStudentId, String sessionFormat) {
        log.info("Inside searchStudentByIdCrossBranch by sessionFormat");
        return academicStudentRepository
                .findBySessionFormatAndAcademicStudentId(sessionFormat, academicStudentId);
    }

    /**
     * Global search — returns the LATEST active enrollment for each matching student
     * across ALL schools and academic years. Used for sibling group "Add Manually" search.
     * Deduplication is done at the DB level (MAX id per student_id).
     */
    public List<AcademicStudent> searchGlobalStudentsByName(String name, int page) {
        log.info("Inside searchGlobalStudentsByName - name={}, page={}", name, page);
        Pageable pageable = PageRequest.of(page, 10);
        return academicStudentRepository.findLatestEnrollmentGlobalByName(name, pageable).getContent();
    }

    /**
     * Returns the latest active AcademicStudent enrollment for a given student.id.
     */
    public Optional<AcademicStudent> getLatestEnrollmentForStudent(Long studentId) {
        log.info("Inside getLatestEnrollmentForStudent - studentId={}", studentId);
        List<AcademicStudent> list = academicStudentRepository.findAllActiveByStudentIdOrderByIdDesc(studentId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public AcademicStudent searchStudentById(Long academicStudentId, Long academicYear, Long school) {
        log.info("Inside searchStudentById");
        return academicStudentRepository
                .findByAcademicYearAndSchoolAndAcademicStudentId(academicYear, school, academicStudentId);
    }

    public List<AcademicStudent> searchSiblings(Long academiYear, AcademicStudent academicStudent) {
        log.info("Inside searchSiblings");
        return academicStudentRepository.findAllByAcademicYear(
                academiYear,
                academicStudent.getStudent().getFatherName(),
                academicStudent.getStudent().getMotherName());
    }

    public int countNoOfYearsOfStudent(AcademicStudent academicStudent) {
        log.info("Inside countNoOfYearsOfStudent");
        return academicStudentRepository.countByStudent(academicStudent.getStudent());
    }

    public Optional<AcademicStudent> getAcademicStudent(Long id) {
        return academicStudentRepository.findById(id);
    }

    public List<AcademicStudent> getAllAcademicStudentByGrade(Long medium, Long grade,
                                                               Long section, Long academic, Long school) {
        log.info("Inside getAllAcademicStudentByGrade");
        return academicStudentRepository
                .findAllBySchool_IdAndMedium_IdAndGrade_IdAndSection_IdAndAcademicYear_IdAndStatusIgnoreCase(
                        school, medium, grade, section, academic, "Active");
    }

    public List<AcademicStudent> getAllAcademicStudent(Long academic, Long school) {
        log.info("Inside getAllAcademicStudent");
        return academicStudentRepository
                .findAllBySchool_IdAndAcademicYear_IdAndStatus(school, academic, "Active");
    }

    public Optional<AcademicStudent> getStudentDetailByUuid(UUID uuid, Long academic, Long school) {
        log.info("Inside getStudentDetailByUuid");
        return academicStudentRepository
                .findByUuidAndStatusAndAcademicYear_IdAndSchool_Id(uuid, "Active", academic, school);
    }

    @Transactional
    public String updateGradeSection(Map<String, String> studentData, Long academic, Long school) {
        log.info("Inside updateGradeSection");
        try {
            if (studentData == null || studentData.isEmpty()) {
                return "error#####Data map is empty";
            }
            Long medium  = Long.parseLong(studentData.getOrDefault("mediumId",  "0"));
            Long grade   = Long.parseLong(studentData.getOrDefault("gradeId",   "0"));
            Long section = Long.parseLong(studentData.getOrDefault("sectionId", "0"));
            String uuid   = studentData.getOrDefault("stuId",   null);
            String reason = studentData.getOrDefault("reason",  null);

            AcademicStudent academicStudent = academicStudentRepository
                    .findByUuidAndStatusAndAcademicYear_IdAndSchool_Id(
                            UUID.fromString(uuid), "Active", academic, school)
                    .orElse(null);
            if (academicStudent == null) return "error#####Student record not found";

            Medium  mediumObj  = mediumRepository .findById(medium) .orElse(null);
            Grade   gradeObj   = gradeRepository  .findById(grade)  .orElse(null);
            Section sectionObj = sectionRepository.findById(section).orElse(null);
            if (mediumObj == null || gradeObj == null || sectionObj == null) {
                return "error#####Medium/Grade/Section all are mandatory";
            }
            academicStudent.setMedium(mediumObj);
            academicStudent.setGrade(gradeObj);
            academicStudent.setSection(sectionObj);
            academicStudent.setDescription(
                    academicStudent.getDescription().concat(". ").concat(reason));
            academicStudentRepository.saveAndFlush(academicStudent);
            return "success#####Student: " + academicStudent.getStudent().getStudentName()
                    + " Grade: "   + gradeObj.getGradeName()
                    + " Section: " + sectionObj.getSectionName()
                    + " updated successfully.";
        } catch (NumberFormatException e) {
            return "error#####Invalid number format: " + e.getMessage();
        } catch (IllegalArgumentException e) {
            return "error#####Invalid UUID format: " + e.getMessage();
        } catch (Exception e) {
            e.printStackTrace();
            return "error#####" + e.getLocalizedMessage();
        }
    }

    public Optional<AcademicStudent> findById(Long academicStudentId) {
        log.info("Inside findById");
        return academicStudentRepository.findById(academicStudentId);
    }

    public List<AcademicStudent> searchStudentsAll(String qry, String academicYear, Long school) {
        log.info("Inside searchStudentsAll - qry={}, academicYear={}, school={}", qry, academicYear, school);
        return academicStudentRepository
                .findAllByAcademicYearAndSchoolAndStudentNames(Long.valueOf(academicYear), school, qry);
    }

    // ── Mobile login helpers ──────────────────────────────────────────────────

    /** Finds a single active student by SR number (classSrNo). */
    public Optional<AcademicStudent> findActiveByClassSrNo(String classSrNo) {
        log.info("Inside findActiveByClassSrNo");
        return academicStudentRepository.findActiveByClassSrNo(classSrNo);
    }

    /** Finds all active students whose parent mobile1 matches. */
    public List<AcademicStudent> findActiveByMobile(String mobile) {
        log.info("Inside findActiveByMobile");
        return academicStudentRepository.findActiveByMobile(mobile);
    }

    /**
     * Finds all active AcademicStudents linked to a FamilyAccount (via FK).
     * Fallback when no SiblingGroup exists for the parent mobile.
     */
    public List<AcademicStudent> findActiveByFamilyAccount(FamilyAccount familyAccount) {
        log.info("Inside findActiveByFamilyAccount");
        return academicStudentRepository.findActiveByFamilyAccount(familyAccount);
    }

    /**
     * PRIMARY child lookup at mobile login.
     * Finds all active siblings via SiblingGroup (admin-defined family grouping).
     * Returns empty list if no SiblingGroup has been set up — caller falls back
     * to findActiveByFamilyAccount().
     */
    public List<AcademicStudent> findSiblingsByMobile(String mobile) {
        log.info("Inside findSiblingsByMobile");
        return academicStudentRepository.findSiblingsByMobile(mobile);
    }

    /** Grades that actually have enrolled active students — for summary report sidebar */
    public List<Grade> findEnrolledGrades(Long schoolId, Long academicYearId) {
        log.info("Inside findEnrolledGrades");
        return academicStudentRepository.findEnrolledGrades(schoolId, academicYearId);
    }

    /** Sections that actually have enrolled active students — for summary report sidebar */
    public List<Section> findEnrolledSections(Long schoolId, Long academicYearId) {
        log.info("Inside findEnrolledSections");
        return academicStudentRepository.findEnrolledSections(schoolId, academicYearId);
    }
}
