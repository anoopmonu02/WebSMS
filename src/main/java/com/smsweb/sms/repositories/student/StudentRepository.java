package com.smsweb.sms.repositories.student;

import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.models.student.Student;
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

    public List<Student> findAllByStudentNameContainingIgnoreCaseAndSchool_IdAndStatus(String name, Long school_id, String status);

    public List<Student> findAllByStatus(String status);

    @Query("""
    SELECT s FROM Student s
    LEFT JOIN FETCH s.grade
    LEFT JOIN FETCH s.section
    LEFT JOIN FETCH s.medium
    LEFT JOIN FETCH s.school sc
    LEFT JOIN FETCH sc.customer
    LEFT JOIN FETCH s.academicYear
    WHERE s.id = :studentId
    AND s.school.id = :branchId
    AND s.academicYear.id = :sessionId
""")
    Optional<Student> findStudentProfile(
            @Param("studentId") Long studentId,
            @Param("branchId")  Long branchId,
            @Param("sessionId") Long sessionId
    );
}
