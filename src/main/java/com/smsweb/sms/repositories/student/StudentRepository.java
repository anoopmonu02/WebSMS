package com.smsweb.sms.repositories.student;

import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.models.student.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, Long> {

    List<Student> findAllBySchool_IdAndStatusOrderByStudentNameAsc(Long school_id, String status);
    List<Student> findAllBySchool_IdOrderByStudentNameAsc(Long school_id);

    Optional<Student> findByIdAndSchool_Id(Long id, Long school_id);
    Optional<Student> findByUuidAndStatusAndSchool_Id(UUID uuid, String status, Long school_id);

    /** Diagnostic lookup (no school/status filter) — used to give a precise reason
     * (not found vs. inactive vs. wrong school) when a Regional-Details upload row fails to match. */
    Optional<Student> findByUuid(UUID uuid);

    public List<Student> findAllByStudentNameContainingIgnoreCaseAndSchool_IdAndStatus(String name, Long school_id, String status);

    public List<Student> findAllByStatus(String status);

    /** Used by FamilyAccountService to link siblings via mobile1. */
    List<Student> findAllByMobile1(String mobile1);

    /** Used by FamilyAccountService to link students whose mobile2 matches a family mobile. */
    List<Student> findAllByMobile2(String mobile2);

    /** All active students that have at least one mobile set — used by family migration scan. */
    @Query("SELECT s FROM Student s WHERE s.status = 'Active' AND (s.mobile1 IS NOT NULL AND s.mobile1 <> '' OR s.mobile2 IS NOT NULL AND s.mobile2 <> '')")
    List<Student> findAllActiveWithMobile();

    /**
     * Finds the highest dummy contact number ever assigned (pattern: 0000000000…).
     * Used to initialise the import dummy counter so we never reuse a number.
     */
    @Query("SELECT MAX(s.mobile1) FROM Student s WHERE s.mobile1 LIKE '00000%'")
    Optional<String> findMaxDummyMobile();

    // ── Server-side DataTable queries ─────────────────────────────────────────

    /** Paginated search for a specific school (non-superadmin). */
    @Query(value = "SELECT s FROM Student s WHERE s.school.id = :schoolId AND s.status = 'Active' " +
                   "AND (:search = '' OR LOWER(s.studentName) LIKE LOWER(CONCAT('%',:search,'%')) " +
                   "OR LOWER(s.fatherName) LIKE LOWER(CONCAT('%',:search,'%')) " +
                   "OR LOWER(s.motherName) LIKE LOWER(CONCAT('%',:search,'%')) " +
                   "OR s.mobile1 LIKE CONCAT('%',:search,'%'))",
           countQuery = "SELECT COUNT(s) FROM Student s WHERE s.school.id = :schoolId AND s.status = 'Active' " +
                        "AND (:search = '' OR LOWER(s.studentName) LIKE LOWER(CONCAT('%',:search,'%')) " +
                        "OR LOWER(s.fatherName) LIKE LOWER(CONCAT('%',:search,'%')) " +
                        "OR LOWER(s.motherName) LIKE LOWER(CONCAT('%',:search,'%')) " +
                        "OR s.mobile1 LIKE CONCAT('%',:search,'%'))")
    Page<Student> searchBySchool(@Param("schoolId") Long schoolId, @Param("search") String search, Pageable pageable);

    /** Total active count for a specific school (used as recordsTotal). */
    @Query("SELECT COUNT(s) FROM Student s WHERE s.school.id = :schoolId AND s.status = 'Active'")
    long countActiveBySchool(@Param("schoolId") Long schoolId);

    /** Paginated search across all schools (superadmin). */
    @Query(value = "SELECT s FROM Student s WHERE s.status = 'ACTIVE' " +
                   "AND (:search = '' OR LOWER(s.studentName) LIKE LOWER(CONCAT('%',:search,'%')) " +
                   "OR LOWER(s.fatherName) LIKE LOWER(CONCAT('%',:search,'%')) " +
                   "OR LOWER(s.motherName) LIKE LOWER(CONCAT('%',:search,'%')) " +
                   "OR s.mobile1 LIKE CONCAT('%',:search,'%'))",
           countQuery = "SELECT COUNT(s) FROM Student s WHERE s.status = 'ACTIVE' " +
                        "AND (:search = '' OR LOWER(s.studentName) LIKE LOWER(CONCAT('%',:search,'%')) " +
                        "OR LOWER(s.fatherName) LIKE LOWER(CONCAT('%',:search,'%')) " +
                        "OR LOWER(s.motherName) LIKE LOWER(CONCAT('%',:search,'%')) " +
                        "OR s.mobile1 LIKE CONCAT('%',:search,'%'))")
    Page<Student> searchAll(@Param("search") String search, Pageable pageable);

    /** Total active count across all schools (superadmin, used as recordsTotal). */
    @Query("SELECT COUNT(s) FROM Student s WHERE s.status = 'ACTIVE'")
    long countAllActive();

    /**
     * Global name search across ALL schools — used for sibling group "Add Manually" section.
     * Matches student name, father name, or mother name. No school or academic year filter.
     */
    @Query("SELECT s FROM Student s WHERE UPPER(s.status) = 'ACTIVE' AND " +
           "(LOWER(s.studentName) LIKE LOWER(CONCAT('%',:name,'%')) OR " +
           "LOWER(s.fatherName) LIKE LOWER(CONCAT('%',:name,'%')) OR " +
           "LOWER(s.motherName) LIKE LOWER(CONCAT('%',:name,'%')))")
    Page<Student> findAllActiveByName(@Param("name") String name, Pageable pageable);
}
