package com.smsweb.sms.repositories.grievance;

import com.smsweb.sms.models.grievance.Grievance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;

public interface GrievanceRepository extends JpaRepository<Grievance, Long> {

    @Query("SELECT DISTINCT g FROM Grievance g LEFT JOIN FETCH g.academicStudent astu " +
            "LEFT JOIN FETCH astu.student LEFT JOIN FETCH astu.grade LEFT JOIN FETCH astu.section " +
            "WHERE g.academicStudent.id = :academicStudentId " +
            "ORDER BY g.createdAt DESC")
    List<Grievance> findAllByAcademicStudentIdOrderByCreatedAtDesc(@Param("academicStudentId") Long academicStudentId);

    /**
     * Dashboard "pending grievances" panel — anything due today or earlier
     * (overdue included, deliberately not restricted to due_date = today) that
     * hasn't been closed yet, scoped to the current school + academic year.
     */
    @Query("SELECT DISTINCT g FROM Grievance g LEFT JOIN FETCH g.academicStudent astu " +
            "LEFT JOIN FETCH astu.student LEFT JOIN FETCH astu.grade LEFT JOIN FETCH astu.section " +
            "WHERE g.closedAt IS NULL " +
            "  AND g.dueDate <= :today " +
            "  AND g.school.id = :schoolId " +
            "  AND g.academicYear.id = :academicYearId " +
            "ORDER BY g.dueDate ASC")
    List<Grievance> findPendingDueTodayOrOverdue(@Param("today") Date today,
                                                  @Param("schoolId") Long schoolId,
                                                  @Param("academicYearId") Long academicYearId);
}
