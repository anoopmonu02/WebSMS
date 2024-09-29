package com.smsweb.sms.repositories.student;

import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.models.student.Student;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentRepository extends JpaRepository<Student, Long> {

    List<Student> findAllBySchool_IdAndStatusOrderByStudentNameAsc(Long school_id, String status);
    List<Student> findAllBySchool_IdOrderByStudentNameAsc(Long school_id);

    Optional<Student> findByIdAndSchool_Id(Long id, Long school_id);
    Optional<Student> findByUuidAndStatusAndSchool_Id(UUID uuid, String status, Long school_id);

    public List<Student> findAllByStudentNameContainingIgnoreCaseAndSchool_IdAndStatus(String name, Long school_id, String status);
}
