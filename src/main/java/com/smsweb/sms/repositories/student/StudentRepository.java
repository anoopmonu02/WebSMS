package com.smsweb.sms.repositories.student;

import com.smsweb.sms.models.student.Student;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    List<Student> findAllBySchool_IdAndStatusOrderByStudentNameAsc(Long school_id, String status);
    List<Student> findAllBySchool_IdOrderByStudentNameAsc(Long school_id);

    Optional<Student> findByIdAndSchool_Id(Long id, Long school_id);
}
