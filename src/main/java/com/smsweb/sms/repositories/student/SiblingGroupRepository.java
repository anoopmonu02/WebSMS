package com.smsweb.sms.repositories.student;

import com.smsweb.sms.models.student.SiblingGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SiblingGroupRepository extends JpaRepository<SiblingGroup, Long> {
    List<SiblingGroup> findAllBySchool_IdAndAcademicYear_IdAndStatus(Long school_id, Long academic_id, String status);

/*
    @Query("SELECT sg FROM SiblingGroup sg WHERE sg.school.id = :schoolId AND sg.academicYear.id = :academicYearId AND sg.status = :status GROUP BY sg.groupName")
    List<SiblingGroup> findSiblingGroupByGroupName(@Param("schoolId") Long schoolId,
                                                   @Param("academicYearId") Long academicYearId,
                                                   @Param("status") String status);
*/



}
