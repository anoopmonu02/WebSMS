package com.smsweb.sms.repositories.tc;

import com.smsweb.sms.models.tc.StudentExecution;
import com.smsweb.sms.models.tc.enums.EventType;
import com.smsweb.sms.models.tc.enums.RefType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentExecutionRepository extends JpaRepository<StudentExecution, Long> {

    /** Timeline of one student record, oldest first (event date, then id). */
    List<StudentExecution> findByStudent_IdAndSchool_IdOrderByEventDateAscIdAsc(Long studentId, Long schoolId);

    Optional<StudentExecution> findByUuidAndSchool_Id(UUID uuid, Long schoolId);

    /**
     * The latest event of one type that points at one record (for example the REACTIVATED row of an
     * exit). Used to show who did it and why without extra columns on the exit.
     */
    Optional<StudentExecution> findFirstByRefTypeAndRefIdAndEventTypeOrderByIdDesc(RefType refType, Long refId, EventType eventType);

    /**
     * The timeline of a chain of student records (old and new records linked by re-registration),
     * oldest first, with the session, class and user loaded so the page runs no extra queries.
     */
    @Query("SELECT e FROM StudentExecution e JOIN FETCH e.academicYear JOIN FETCH e.student " +
            "LEFT JOIN FETCH e.grade LEFT JOIN FETCH e.section LEFT JOIN FETCH e.createdBy " +
            "WHERE e.student.id IN :studentIds AND e.school.id = :schoolId " +
            "ORDER BY e.eventDate ASC, e.id ASC")
    List<StudentExecution> findTimeline(@Param("studentIds") Collection<Long> studentIds, @Param("schoolId") Long schoolId);
}
