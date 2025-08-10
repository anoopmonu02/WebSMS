package com.smsweb.sms.services.student;

import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.universal.Grade;
import com.smsweb.sms.models.universal.Medium;
import com.smsweb.sms.models.universal.Section;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.repositories.universal.GradeRepository;
import com.smsweb.sms.repositories.universal.MediumRepository;
import com.smsweb.sms.repositories.universal.SectionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AcademicStudentService {
    private final AcademicStudentRepository academicStudentRepository;
    private final MediumRepository mediumRepository;
    private final GradeRepository gradeRepository;
    private final SectionRepository sectionRepository;

    public AcademicStudentService(AcademicStudentRepository academicStudentRepository, MediumRepository mediumRepository, GradeRepository gradeRepository, SectionRepository sectionRepository){
        this.academicStudentRepository = academicStudentRepository;
        this.mediumRepository = mediumRepository;
        this.gradeRepository = gradeRepository;
        this.sectionRepository = sectionRepository;
    }

    public List<AcademicStudent> searchStudents(String stuname, Long academicYear, Long school){
        Pageable pageable = PageRequest.of(0, 10);
        return academicStudentRepository.findAllByAcademicYearAndSchoolAndStudentName(academicYear, school, stuname, pageable).getContent();
    }

    public List<AcademicStudent> searchStudentsFromAllBranches(String stuname, Long academicYear){
        Pageable pageable = PageRequest.of(0, 10);
        return academicStudentRepository.findAllByAcademicYearAndStudentName(academicYear, stuname, pageable).getContent();
    }
    public AcademicStudent searchStudentById(Long academicStudentId, Long academicYear, Long school){
        return academicStudentRepository.findByAcademicYearAndSchoolAndAcademicStudentId(academicYear, school, academicStudentId);
    }

    public List<AcademicStudent> searchSiblings(Long academiYear, AcademicStudent academicStudent){
        return academicStudentRepository.findAllByAcademicYear(academiYear, academicStudent.getStudent().getFatherName(), academicStudent.getStudent().getMotherName());
    }

    public int countNoOfYearsOfStudent(AcademicStudent academicStudent){
        return academicStudentRepository.countByStudent(academicStudent.getStudent());
    }

    public Optional<AcademicStudent> getAcademicStudent(Long id){
        return academicStudentRepository.findById(id);
    }

    public List<AcademicStudent> getAllAcademicStudentByGrade(Long medium, Long grade, Long section, Long academic, Long school){
        return academicStudentRepository.findAllBySchool_IdAndMedium_IdAndGrade_IdAndSection_IdAndAcademicYear_IdAndStatus(school, medium, grade, section, academic, "Active");
    }

    public List<AcademicStudent> getAllAcademicStudent(Long academic, Long school){
        return academicStudentRepository.findAllBySchool_IdAndAcademicYear_IdAndStatus(school, academic, "Active");
    }

    public Optional<AcademicStudent> getStudentDetailByUuid(UUID uuid, Long academic, Long school){
        return academicStudentRepository.findByUuidAndStatusAndAcademicYear_IdAndSchool_Id(uuid, "Active", academic, school);
    }

    @Transactional
    public String updateGradeSection(Map<String, String> studentData, Long academic, Long school){
        try{
            if (studentData == null || studentData.isEmpty()) {
                return "error#####Data map is empty";
            }
            Long medium = Long.parseLong(studentData.getOrDefault("mediumId", "0"));
            Long grade = Long.parseLong(studentData.getOrDefault("gradeId", "0"));
            Long section = Long.parseLong(studentData.getOrDefault("sectionId", "0"));
            String uuid = studentData.getOrDefault("stuId",null);
            String reason = studentData.getOrDefault("reason",null);
            AcademicStudent academicStudent = academicStudentRepository.findByUuidAndStatusAndAcademicYear_IdAndSchool_Id(UUID.fromString(uuid), "Active", academic, school).orElse(null);
            if(academicStudent==null){
                return "error#####Student record not found";
            }
            Medium mediumObj = mediumRepository.findById(medium).orElse(null);
            Grade gradeObj = gradeRepository.findById(grade).orElse(null);
            Section sectionObj = sectionRepository.findById(section).orElse(null);
            if(mediumObj==null || gradeObj==null || sectionObj==null){
                return "error#####Medium/Grade/Section all are mandatory";
            }
            academicStudent.setMedium(mediumObj);
            academicStudent.setGrade(gradeObj);
            academicStudent.setSection(sectionObj);
            academicStudent.setDescription(academicStudent.getDescription().concat(". ").concat(reason));
            academicStudentRepository.saveAndFlush(academicStudent);
            return "success#####Student: " + academicStudent.getStudent().getStudentName() + " Grade: " + gradeObj.getGradeName() + " Section: " + sectionObj.getSectionName() + " updated successfully.";
        } catch (NumberFormatException e) {
            return "error#####Invalid number format: " + e.getMessage();
        } catch (IllegalArgumentException e) {
            return "error#####Invalid UUID format: " + e.getMessage();
        } catch(Exception e){
            e.printStackTrace();
            return "error#####" + e.getLocalizedMessage();
        }
    }

    public Optional<AcademicStudent> findById(Long academicStudentId) {
        return academicStudentRepository.findById(academicStudentId);
    }

    public List<AcademicStudent> searchStudentsAll(String qry, Long academicYear, Long school){
        //Pageable pageable = PageRequest.of(0, 10);
        System.out.println("qry: "+qry+"-");
        System.out.println("academicYear: "+academicYear);
        System.out.println("school: "+school);
        return academicStudentRepository.findAllByAcademicYearAndSchoolAndStudentNames(academicYear, school, qry);
    }
}
