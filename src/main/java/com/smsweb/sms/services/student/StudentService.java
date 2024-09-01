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
import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public StudentService(StudentRepository repository, AcademicStudentRepository academicStudentRepository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.academicStudentRepository = academicStudentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Student> getAllActiveStudents(Long school_id) {
        return repository.findAllBySchool_IdAndStatusOrderByStudentNameAsc(school_id, "Active");
    }

    public List<Student> getAllStudents(Long school_id) {
        return repository.findAllBySchool_IdOrderByStudentNameAsc(school_id);
    }

    public Optional<Student> getStudentDetail(Long student_id, Long school_id) {
        return repository.findByIdAndSchool_Id(student_id, school_id);
    }

    @Transactional
    public Student saveStudent(Student student, MultipartFile logo, String fileNameOrSchoolCode) throws IOException {
        Student existingStudent = null;
        String imageResponse = new FileHandleHelper().copyImageToGivenDirectory(logo, "students");
        if (imageResponse != null && (imageResponse.equalsIgnoreCase("success") || imageResponse.equalsIgnoreCase("Success_no_image"))) {
            try {
                //Handle Student Image
                if (!imageResponse.equalsIgnoreCase("Success_no_image")) {
                    student.setPic(fileNameOrSchoolCode + "_" + logo.getOriginalFilename());
                } else {
                    //  if student is going to update without new pic selection happen
                    try{
                        existingStudent = repository.findById(student.getId()).orElse(null);
                    }catch(ObjectNotFoundException e){
                        existingStudent = null;
                    }
                    if (imageResponse.equalsIgnoreCase("Success_no_image") && existingStudent != null) {
                        student.setPic(existingStudent.getPic());
                    } else {
                        student.setPic(null);
                    }
                }
                student.setRegistrationNo("SRN-" + fileNameOrSchoolCode);
                if (existingStudent != null && existingStudent.getRegistrationNo().length() > 0) {
                    student.setRegistrationNo(existingStudent.getRegistrationNo());
                }
                if(existingStudent==null){
                    // Set username as registration number
                    student.setUsername(student.getRegistrationNo());

                    // Generate password
                    String password = generatePassword(student.getRegistrationNo(), student.getMobile1());
                    student.setPassword(passwordEncoder.encode(password));
                }

                Student savedStudent = repository.save(student);
                if(existingStudent==null){
                    AcademicStudent academicStudent = new AcademicStudent();
                    academicStudent.setAcademicYear(savedStudent.getAcademicYear());
                    academicStudent.setSchool(savedStudent.getSchool());
                    academicStudent.setGrade(savedStudent.getGrade());
                    academicStudent.setMedium(savedStudent.getMedium());
                    academicStudent.setSection(savedStudent.getSection());
                    academicStudent.setDescription("Saving at time of student creation.");
                    academicStudent.setStudent(savedStudent);
                    academicStudentRepository.save(academicStudent);
                }
                return savedStudent;

            } catch (DataIntegrityViolationException ed) {
                throw new UniqueConstraintsException("Student Name: " + student.getStudentName() + " already exists.", ed);
            } catch (Exception e) {
                e.printStackTrace();
                throw new ObjectNotSaveException("Failed to save student", e);
            }
        } else if (imageResponse.equalsIgnoreCase("fail")) {
            throw new FileFormatException("Fail to save pic");
        } else if (imageResponse.equalsIgnoreCase("Either image format not supported or size exceeded 2MB.")) {
            throw new FileSizeLimitExceededException("Either image format not supported or size exceeded 2MB.");
        }
        return null;
    }

    public List<Student> searchStudent(String stuname) {
        return repository.findAllByStudentNameContainingIgnoreCaseAndSchool_IdAndStatus(stuname, 4L, "Active");
    }

    @Transactional
    public Long updateContact(String contactNo, Long studentId) {
        try {
            Student student = repository.findById(studentId).orElse(null);
            if (student != null) {
                student.setMobile1(contactNo);
                repository.save(student);
                return studentId;
            }
        } catch (Exception e) {
            throw new ObjectNotSaveException("Unable to update contact number. Error: " + e.getLocalizedMessage(), e);
        }
        return 0L;
    }

    private String generatePassword(String registrationNo, String mobileNumber) {
        // Extract the last 6 digits of the registration number
        String lastSixDigitsOfRegistrationNo = registrationNo.length() >= 6
                ? registrationNo.substring(registrationNo.length() - 6)
                : registrationNo;

        // Extract the last 4 digits of the mobile number
        String lastFourDigitsOfMobileNumber = mobileNumber != null && mobileNumber.length() >= 4
                ? mobileNumber.substring(mobileNumber.length() - 4)
                : "";

        // Concatenate the two parts to form the password
        return lastSixDigitsOfRegistrationNo + lastFourDigitsOfMobileNumber;
    }

    @Transactional
    public Student editStudentDetails(Student student, MultipartFile logo, String fileNameOrSchoolCode) throws IOException {
        try{
            Student existingStudent = null;
            existingStudent = repository.findById(student.getId()).orElseThrow(()->new RuntimeException("Student not found"));
            if(!logo.isEmpty()){
                String imageResponse = new FileHandleHelper().copyImageToGivenDirectory(logo, "students");
                if(imageResponse.equalsIgnoreCase("Fail")){
                    throw new RuntimeException("Image processing fails");
                }
                if(imageResponse.equalsIgnoreCase("success")){
                    existingStudent.setPic(fileNameOrSchoolCode + "_" + logo.getOriginalFilename());
                } else{
                    throw new FileFormatException("File format not supported or size exceeded.");
                }
            } else{
                //handle if image not selected but already saved previously
                if(existingStudent.getPic()!=null){

                }
            }
            existingStudent.setStudentName(student.getStudentName());
            existingStudent.setFatherName(student.getFatherName());
            existingStudent.setFatherOccupation(student.getFatherOccupation());
            existingStudent.setMotherName(student.getMotherName());
            existingStudent.setMotherOccupation(student.getMotherOccupation());
            existingStudent.setReligion(student.getReligion());
            existingStudent.setGender(student.getGender());
            existingStudent.setCategory(student.getCategory());
            existingStudent.setCast(student.getCast());
            existingStudent.setDescription(student.getDescription());
            existingStudent.setHeight(student.getHeight());
            existingStudent.setWeight(student.getWeight());
            existingStudent.setBloodGroup(student.getBloodGroup());
            existingStudent.setBodyType(student.getBodyType());
            existingStudent.setAddress(student.getAddress());
            existingStudent.setLandmark(student.getLandmark());
            existingStudent.setProvince(student.getProvince());
            existingStudent.setCity(student.getCity());
            existingStudent.setPincode(student.getPincode());
            existingStudent.setMobile1(student.getMobile1());
            existingStudent.setMobile2(student.getMobile2());
            existingStudent.setEmail(student.getEmail());
            existingStudent.setPersonName(student.getPersonName());
            existingStudent.setPersonContact(student.getPersonContact());
            existingStudent.setRelationship(student.getRelationship());

            existingStudent = repository.saveAndFlush(existingStudent);

        }catch(Exception e){
            e.printStackTrace();
            throw new ObjectNotSaveException("Unable to update student: "+student.getStudentName()+". Error: " + e.getLocalizedMessage(), e);
        }
        return null;
    }

    public List<AcademicStudent> getAllStudentsByGrade(Long medium, Long grade, Long section, Long academic, Long school){
        return academicStudentRepository.findAllBySchool_IdAndMedium_IdAndGrade_IdAndSection_IdAndAcademicYear_IdAndStatus(school, medium, grade, section, academic, "Active");
    }
}
