package com.smsweb.sms.services.student;

import com.smsweb.sms.exceptions.FileFormatException;
import com.smsweb.sms.exceptions.FileSizeLimitExceededException;
import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.exceptions.UniqueConstraintsException;
import com.smsweb.sms.helper.FileHandleHelper;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.repositories.student.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class StudentService {

    private final StudentRepository repository;
    private final AcademicStudentRepository academicStudentRepository;

    @Autowired
    public StudentService(StudentRepository repository, AcademicStudentRepository academicStudentRepository){
        this.repository = repository;
        this.academicStudentRepository = academicStudentRepository;
    }


    public List<Student> getAllActiveStudents(Long school_id){
        return repository.findAllBySchool_IdAndStatusOrderByStudentNameAsc(school_id, "Active");
    }

    public List<Student> getAllStudents(Long school_id){
        return repository.findAllBySchool_IdOrderByStudentNameAsc(school_id);
    }

    public Optional<Student> getStudentDetail(Long student_id, Long school_id){
        return repository.findByIdAndSchool_Id(student_id, school_id);
    }

    @Transactional
    public Student saveStudent(Student student, MultipartFile logo, String fileNameOrSchoolCode) throws IOException {
        Student existingStudent=null;
        String imageResponse = new FileHandleHelper().copyImageToGivenDirectory(logo, "students");
        if(imageResponse!=null && (imageResponse.equalsIgnoreCase("success") || imageResponse.equalsIgnoreCase("Success_no_image"))){
            try{
                if(!imageResponse.equalsIgnoreCase("Success_no_image")){
                    student.setPic(fileNameOrSchoolCode+"_"+logo.getOriginalFilename());
                } else{
                    // if student is going to update without new pic selection happen
                    if(imageResponse.equalsIgnoreCase("Success_no_image") && existingStudent!=null){
                        existingStudent = repository.findById(student.getId()).orElseThrow(() -> new RuntimeException("Student not found"));
                        student.setPic(existingStudent.getPic());
                    } else{
                        student.setPic(null);
                    }
                }
                student.setRegistrationNo("SRN-"+fileNameOrSchoolCode);
                if(existingStudent!=null && existingStudent.getRegistrationNo().length()>0){
                    student.setRegistrationNo(existingStudent.getRegistrationNo());
                }
                Student savedStudent = repository.save(student);
                AcademicStudent academicStudent = new AcademicStudent();
                academicStudent.setAcademicYear(savedStudent.getAcademicYear());
                academicStudent.setSchool(savedStudent.getSchool());
                academicStudent.setGrade(savedStudent.getGrade());
                academicStudent.setMedium(savedStudent.getMedium());
                academicStudent.setSection(savedStudent.getSection());
                academicStudent.setDescription("Saving at time of student creation.");
                academicStudent.setStudent(savedStudent);
                academicStudentRepository.save(academicStudent);
                return savedStudent;
            }catch (DataIntegrityViolationException ed) {
                throw new UniqueConstraintsException("Student Name: "+student.getStudentName()+" already exists.", ed);
            }catch(Exception e){
                e.printStackTrace();
                throw new ObjectNotSaveException("Failed to save student", e);
            }
        } else if(imageResponse.equalsIgnoreCase("fail")){
            throw new FileFormatException("Fail to save pic");
        } else if(imageResponse.equalsIgnoreCase("Either image format not supported or size exceeded 2MB.")){
            throw new FileSizeLimitExceededException("Either image format not supported or size exceeded 2MB.");
        }
        return null;
    }

    public List<Student> searchStudent(String stuname){
        return repository.findAllByStudentNameContainingIgnoreCaseAndSchool_IdAndStatus(stuname, 4L, "Active");
    }

    @Transactional
    public Long updateContact(String contactNo, Long studentId){
        try{
            Student student = repository.findById(studentId).orElse(null);
            if(student!=null){
                student.setMobile1(contactNo);
                repository.save(student);
                return studentId;
            }
        }catch(Exception e){
            throw new ObjectNotSaveException("Unable to update contact number. Error: "+e.getLocalizedMessage(), e);
        }
        return 0L;
    }

}
