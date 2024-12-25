package com.smsweb.sms.services.student;

import com.smsweb.sms.controllers.employee.EmployeeController;
import com.smsweb.sms.exceptions.FileFormatException;
import com.smsweb.sms.exceptions.FileSizeLimitExceededException;
import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.helper.FileHandleHelper;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.repositories.student.StudentRepository;
import com.smsweb.sms.services.users.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class StudentService {
    public static final Logger log = LoggerFactory.getLogger(EmployeeController.class);
    private final StudentRepository repository;
    private final AcademicStudentRepository academicStudentRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileHandleHelper fileHandleHelper;
    private final UserService userService;

    @Autowired
    public StudentService(StudentRepository repository, AcademicStudentRepository academicStudentRepository, PasswordEncoder passwordEncoder, FileHandleHelper fileHandleHelper, UserService userService) {
        this.repository = repository;
        this.academicStudentRepository = academicStudentRepository;
        this.passwordEncoder = passwordEncoder;
        this.fileHandleHelper = fileHandleHelper;
        this.userService = userService;
    }

    public List<Student> getAllActiveStudents(Long school_id) {
        return repository.findAllBySchool_IdAndStatusOrderByStudentNameAsc(school_id, "Active");
    }

    public List<Student> getAllInActiveStudents(Long school_id) {
        return repository.findAllBySchool_IdAndStatusOrderByStudentNameAsc(school_id, "Inactive");
    }

    public List<Student> getAllStudents(Long school_id) {
        return repository.findAllBySchool_IdOrderByStudentNameAsc(school_id);
    }

    public Optional<Student> getStudentDetail(Long student_id, Long school_id) {
        return repository.findByIdAndSchool_Id(student_id, school_id);
    }

    public Optional<Student> getStudentDetail(UUID uuid, Long school_id) {
        return repository.findByUuidAndStatusAndSchool_Id(uuid, "Active", school_id);
    }
    public Optional<Student> getDeletedStudentDetail(UUID uuid, Long school_id) {
        return repository.findByUuidAndStatusAndSchool_Id(uuid, "Inactive", school_id);
    }

    @Transactional
    public Student saveStudent(Student student, MultipartFile logo, String fileNameOrSchoolCode, Student existingStudent) throws IOException {
        String imageResponse = fileHandleHelper.saveImage("student", logo);
        try{
            boolean proceedFlag = false;
            //Saving Student Data
            if(existingStudent==null){
                student.setRegistrationNo("SRN-"+fileNameOrSchoolCode);
                // Set username as registration number
                UserEntity userEntity = new UserEntity();
                userEntity.setUsername(student.getRegistrationNo());
                // Generate password
                String password = generatePassword(student.getRegistrationNo(), student.getMobile1());
                userEntity.setPassword(passwordEncoder.encode(password));
                userEntity.setEmail(student.getUserEntity().getEmail());
                student.setUserEntity(userEntity);
            } else if(existingStudent!=null){
                if(existingStudent.getPic()!=null && existingStudent.getPic()!=""){
                    student.setPic(existingStudent.getPic());
                    student.setRegistrationNo(existingStudent.getRegistrationNo());
                }
            }
            Student savedStudent = repository.save(student);
            if(savedStudent!=null){
                proceedFlag = true;
            }
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
            boolean foundImageResponse = (imageResponse!=null && imageResponse!="")?true:false;
            if(foundImageResponse && imageResponse.equalsIgnoreCase("Success_no_image")){
                proceedFlag = true;
                if(existingStudent!=null){
                    if(existingStudent.getPic()!=null && existingStudent.getPic()!=""){
                        student.setPic(existingStudent.getPic());
                    }
                }
            } else if(foundImageResponse && imageResponse.equalsIgnoreCase("Either image format not supported or size exceeded 2MB.")){
                throw new FileSizeLimitExceededException("Either image format not supported or size exceeded 2MB.");
            } else if (foundImageResponse && imageResponse.startsWith("Failed to save the image: ")) {
                throw new FileFormatException(imageResponse);
            } else if (foundImageResponse && imageResponse.equalsIgnoreCase("Specified category not valid")) {
                throw new RuntimeException(imageResponse);
            } else{
                student.setPic(imageResponse);
                proceedFlag = true;
            }
            if(proceedFlag){
                savedStudent = repository.save(student);
            }
            /*if(proceedFlag){
                if(existingStudent==null){
                    // Set username as registration number
                    UserEntity userEntity = new UserEntity();
                    userEntity.setUsername(student.getRegistrationNo());
                    // Generate password
                    String password = generatePassword(student.getRegistrationNo(), student.getMobile1());
                    userEntity.setPassword(passwordEncoder.encode(password));
                    userEntity.setEmail(student.getUserEntity().getEmail());
                    student.setUserEntity(userEntity);
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
            }*/
            return savedStudent;
        }catch(Exception e){
            log.debug("error while saving Student--"+e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    public List<Student> searchStudent(String stuname) {
        //TODO - Check the usage of method and fix
        return repository.findAllByStudentNameContainingIgnoreCaseAndSchool_IdAndStatus(stuname, 4L, "Active");
    }

    @Transactional
    public Long updateContact(String contactNo, Long studentId) {
        try {
            Student student = repository.findById(studentId).orElse(null);
            if (student != null) {
                student.setMobile1(contactNo);
                student.setUpdatedBy(userService.getLoggedInUser().getUsername());
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
            if(existingStudent!=null){
                if(!logo.isEmpty()){
                    String imageResponse = fileHandleHelper.saveImage("student", logo);

                    boolean proceedFlag = false;
                    boolean foundImageResponse = (imageResponse!=null && imageResponse!="")?true:false;
                    if(foundImageResponse && imageResponse.equalsIgnoreCase("Either image format not supported or size exceeded 2MB.")){
                        throw new FileSizeLimitExceededException("Either image format not supported or size exceeded 2MB.");
                    } else if (foundImageResponse && imageResponse.startsWith("Failed to save the image: ")) {
                        throw new FileFormatException(imageResponse);
                    } else if (foundImageResponse && imageResponse.equalsIgnoreCase("Specified category not valid")) {
                        throw new RuntimeException(imageResponse);
                    } else{
                        existingStudent.setPic(imageResponse);
                        existingStudent.setRegistrationNo(student.getRegistrationNo());
                        proceedFlag = true;
                    }
                } else{
                    //handle if image not selected but already saved previously
                    if(existingStudent.getPic()!=null){
                        String picnm = existingStudent.getPic();
                        existingStudent.setPic(picnm);
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
                existingStudent.getUserEntity().setEmail(student.getUserEntity().getEmail());
                existingStudent.setPersonName(student.getPersonName());
                existingStudent.setPersonContact(student.getPersonContact());
                existingStudent.setRelationship(student.getRelationship());
                existingStudent.setUpdatedBy(userService.getLoggedInUser().getUsername());
                existingStudent = repository.saveAndFlush(existingStudent);
                return existingStudent;
            }

        }catch(Exception e){
            e.printStackTrace();
            throw new ObjectNotSaveException("Unable to update student: "+student.getStudentName()+". Error: " + e.getLocalizedMessage(), e);
        }
        return null;
    }

    public List<AcademicStudent> getAllStudentsByGrade(Long medium, Long grade, Long section, Long academic, Long school){
        return academicStudentRepository.findAllBySchool_IdAndMedium_IdAndGrade_IdAndSection_IdAndAcademicYear_IdAndStatus(school, medium, grade, section, academic, "Active");
    }

    @Transactional
    public String deleteStudent(Long id){
        String msg = "";
        try{
            List<AcademicStudent> academicList = academicStudentRepository.findAllByStudent_IdAndStatus(id, "Active");

            if(academicList == null || academicList.isEmpty()){
                return "success#####Student not found";
            }
            for(AcademicStudent academicStudent : academicList){
                academicStudent.setStatus("Inactive");
            }
            academicStudentRepository.saveAll(academicList);

            Student student = academicList.get(0).getStudent();

            student.setStatus("Inactive");
            student = repository.save(student);
            msg = "success#####Student: " + student.getStudentName() + " deleted successfully";

        }catch(Exception e){
            msg = "error#####"+e.getLocalizedMessage();
        }
        return msg;
    }

    @Transactional
    public String uploadSR(List<Map<String, String>> srdata, Long academic, Long school){
        int SRFailCounter = 0, srPassCounter = 0;
        List<AcademicStudent> studentsToSave = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();
        try{
            for (Map<String, String> rowData : srdata) {
                if (rowData.containsKey("SR") && rowData.get("SR")!=null && !rowData.get("SR").isEmpty()) {
                    String uuid = rowData.get("ID#");
                    if (uuid != null && !uuid.isEmpty()) {
                        AcademicStudent academicStudent = academicStudentRepository.findByUuidAndStatusAndAcademicYear_IdAndSchool_Id(
                                UUID.fromString(uuid), "Active", academic, school).orElse(null);

                        if (academicStudent != null) {
                            academicStudent.setClassSrNo(rowData.get("SR"));
                            studentsToSave.add(academicStudent);  // Collect the student for bulk saving
                            srPassCounter++;
                        } else {
                            failedIds.add(uuid);  // Log the failure
                            SRFailCounter++;
                        }
                    } else {
                        failedIds.add("Invalid UUID");
                        SRFailCounter++;
                    }
                } else {
                    SRFailCounter++;
                }
            }

            // Bulk save the students
            academicStudentRepository.saveAll(studentsToSave);
            return "Total SR updated: " + srPassCounter + " and SR not found for: "+SRFailCounter;
        }catch(Exception e){
            e.printStackTrace();
            return "error#####"+e.getLocalizedMessage();
        }
    }

    @Transactional
    public String uploadSRFromTable(Map<String, String> studentData, Long academic, Long school){
        AtomicInteger SRFailCounter = new AtomicInteger();
        AtomicInteger srPassCounter = new AtomicInteger();
        List<AcademicStudent> studentsToSave = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();
        try{
            studentData.forEach((key, value) -> {
                if(key!=null && value!=null && value!=""){
                    String uuid = key.split("sr_")[1];
                    if (uuid != null && !uuid.isEmpty()) {
                        AcademicStudent academicStudent = academicStudentRepository.findByUuidAndStatusAndAcademicYear_IdAndSchool_Id(
                                UUID.fromString(uuid), "Active", academic, school).orElse(null);

                        if (academicStudent != null) {
                            academicStudent.setClassSrNo(value);
                            studentsToSave.add(academicStudent);  // Collect the student for bulk saving
                            srPassCounter.getAndIncrement();
                        } else {
                            failedIds.add(uuid);  // Log the failure
                            SRFailCounter.getAndIncrement();
                        }
                    } else {
                        failedIds.add("Invalid UUID");
                        SRFailCounter.getAndIncrement();
                    }
                } else{
                    SRFailCounter.getAndIncrement();
                }
            });

            // Bulk save the students
            academicStudentRepository.saveAll(studentsToSave);
            return "Total SR updated: " + srPassCounter + " and SR not found for: "+SRFailCounter;
        }catch(Exception e){
            e.printStackTrace();
            return "error#####"+e.getLocalizedMessage();
        }
    }

}
