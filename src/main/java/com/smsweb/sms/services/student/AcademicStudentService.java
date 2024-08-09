package com.smsweb.sms.services.student;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AcademicStudentService {
    private final AcademicStudentRepository academicStudentRepository;

    public AcademicStudentService(AcademicStudentRepository academicStudentRepository){
        this.academicStudentRepository = academicStudentRepository;
    }

    public List<AcademicStudent> searchStudents(String stuname, Long academicYear, Long school){
        Pageable pageable = PageRequest.of(0, 10);
        return academicStudentRepository.findAllByAcademicYearAndSchoolAndStudentName(academicYear, school, stuname, pageable).getContent();
    }

    public AcademicStudent searchStudentById(Long academicStudentId, Long academicYear, Long school){
        return academicStudentRepository.findByAcademicYearAndSchoolAndAcademicStudentId(academicYear, school, academicStudentId);
    }

    public int countNoOfYearsOfStudent(AcademicStudent academicStudent){
        return academicStudentRepository.countByStudent(academicStudent.getStudent());
    }

    public Optional<AcademicStudent> getAcademicStudent(Long id){
        return academicStudentRepository.findById(id);
    }
}
