package com.smsweb.sms.repositories.mobile;

import com.smsweb.sms.models.student.AcademicStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * NEW repository (feature #7), brand-new package (repositories.mobile).
 * Does NOT modify the existing AcademicStudentRepository.java. Spring Data
 * JPA fully supports multiple repository interfaces bound to the same
 * entity — this is a separate proxy bean, it does not conflict with or
 * change the behavior of the existing AcademicStudentRepository bean that
 * the admin web dashboard already depends on.
 */
public interface MobileAcademicStudentRepository extends JpaRepository<AcademicStudent, Long> {

    /**
     * All AcademicStudent enrollment rows (any status, any year) for one
     * physical student — used to build the "switch academic year" picker on
     * mobile (Attendance / Fees / Results) and to validate that a client-supplied
     * academicStudentId actually belongs to the same underlying student before
     * using it to scope a query. Deliberately does NOT filter by status='Active'
     * — a parent should still be able to view a past (now-inactive) year.
     */
    @Query("SELECT a FROM AcademicStudent a " +
           "JOIN FETCH a.academicYear ay " +
           "JOIN FETCH a.grade g " +
           "JOIN FETCH a.section sec " +
           "JOIN FETCH a.school sc " +
           "WHERE a.student.id = :studentId " +
           "ORDER BY ay.id DESC")
    List<AcademicStudent> findAllByStudentIdOrderByYearDesc(@Param("studentId") Long studentId);

    // findById(Long) is already inherited from JpaRepository — used directly
    // by MobileAcademicYearService, no extra method needed here.
}
