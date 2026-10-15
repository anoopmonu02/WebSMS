package com.smsweb.sms.repositories.student;

import com.smsweb.sms.models.student.SiblingGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SiblingGroupRepository extends JpaRepository<SiblingGroup, Long> {
    List<SiblingGroup> findAllBySchool_IdAndAcademicYear_IdAndStatus(Long school_id, Long academic_id, String status);

    /**
     * Locks the group row until the surrounding transaction ends. Used when assigning the sibling
     * discount so two simultaneous saves for the same group are serialised and the per-group limit
     * can't be exceeded by a race.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM SiblingGroup g WHERE g.id = :id")
    Optional<SiblingGroup> findByIdForUpdate(@Param("id") Long id);

    /** Group names are unique per school + academic year; used to pick a free name when a group is re-formed. */
    boolean existsByGroupNameAndSchool_IdAndAcademicYear_Id(String groupName, Long schoolId, Long academicYearId);

/*
    @Query("SELECT sg FROM SiblingGroup sg WHERE sg.school.id = :schoolId AND sg.academicYear.id = :academicYearId AND sg.status = :status GROUP BY sg.groupName")
    List<SiblingGroup> findSiblingGroupByGroupName(@Param("schoolId") Long schoolId,
                                                   @Param("academicYearId") Long academicYearId,
                                                   @Param("status") String status);
*/



}
