package com.smsweb.sms.repositories.student;

import com.smsweb.sms.models.student.StudentDiscount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentDiscountRepository extends JpaRepository<StudentDiscount, Long> {

    Optional<StudentDiscount> findBySchool_IdAndAcademicYear_IdAndAcademicStudent_IdAndDiscounthead_Id(Long school_id, Long academic_id, Long student_id, Long discount_id);
    Optional<StudentDiscount> findBySchool_IdAndAcademicYear_IdAndAcademicStudent_Id(Long school_id, Long academic_id, Long student_id);

    List<StudentDiscount> findAllBySchool_IdAndAcademicYear_IdAndStatus(Long school_id, Long academic_id, String status);

}
