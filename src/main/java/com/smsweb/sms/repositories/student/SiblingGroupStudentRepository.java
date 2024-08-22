package com.smsweb.sms.repositories.student;

import com.smsweb.sms.models.student.SiblingGroup;
import com.smsweb.sms.models.student.SiblingGroupStudent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SiblingGroupStudentRepository extends JpaRepository<SiblingGroupStudent, Long> {

    List<SiblingGroupStudent> findAllBySiblingGroup(SiblingGroup group);

}
