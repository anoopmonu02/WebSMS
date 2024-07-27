package com.smsweb.sms.services.student;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AcademicStudentService {
    private final AcademicStudentRepository academicStudentRepository;

    public AcademicStudentService(AcademicStudentRepository academicStudentRepository){
        this.academicStudentRepository = academicStudentRepository;
    }

    public List<AcademicStudent> searchStudents(String stuname, Long academicYear, Long school){
        return academicStudentRepository.findAllByAcademicYearAndSchoolAndStudentName(academicYear, school, stuname);
    }

    public AcademicStudent searchStudentById(Long academicStudentId, Long academicYear, Long school){
        return academicStudentRepository.findByAcademicYearAndSchoolAndAcademicStudentId(academicYear, school, academicStudentId);
    }

    public int countNoOfYearsOfStudent(AcademicStudent academicStudent){
        return academicStudentRepository.countByStudent(academicStudent.getStudent());
    }
}
