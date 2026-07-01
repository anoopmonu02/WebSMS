package com.smsweb.sms.repositories.student;

import com.smsweb.sms.models.student.SiblingGroup;
import com.smsweb.sms.models.student.SiblingGroupStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SiblingGroupStudentRepository extends JpaRepository<SiblingGroupStudent, Long> {

    List<SiblingGroupStudent> findAllBySiblingGroup(SiblingGroup group);

    /**
     * Cross-school sibling group check — finds any active SiblingGroupStudent records
     * for the given student.id, regardless of school or academic year.
     * Used to prevent duplicate discounts: if a student is already grouped at Branch A,
     * Branch B cannot create another group for the same physical student.
     */
    @Query("SELECT sgs FROM SiblingGroupStudent sgs " +
           "JOIN FETCH sgs.siblingGroup sg " +
           "JOIN FETCH sg.school " +
           "JOIN sgs.academicStudent acs " +
           "JOIN acs.student s " +
           "WHERE s.id = :studentId AND sg.status = 'Active'")
    List<SiblingGroupStudent> findActiveGroupsByStudentId(@Param("studentId") Long studentId);

}
