package com.smsweb.sms.services.student;

import com.smsweb.sms.controllers.employee.EmployeeController;
import com.smsweb.sms.exceptions.FileFormatException;
import com.smsweb.sms.exceptions.FileSizeLimitExceededException;
import com.smsweb.sms.exceptions.ObjectNotSaveException;
import com.smsweb.sms.helper.FileHandleHelper;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.ExamDetails;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Attendance;
import com.smsweb.sms.models.student.ExamResultSummary;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.repositories.student.AttendanceRepository;
import com.smsweb.sms.repositories.student.ExamResultSummaryRepository;
import com.smsweb.sms.repositories.student.StudentRepository;
import com.smsweb.sms.services.admin.ExaminationService;
import com.smsweb.sms.services.users.UserService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class StudentService {
    public static final Logger log = LoggerFactory.getLogger(EmployeeController.class);
    private final StudentRepository repository;
    private final AcademicStudentRepository academicStudentRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileHandleHelper fileHandleHelper;
    private final UserService userService;
    private final AttendanceRepository attendanceRepository;
    private final ExaminationService examinationService;
    private final ExamResultSummaryRepository examResultSummaryRepository;

    @Autowired
    public StudentService(StudentRepository repository, AcademicStudentRepository academicStudentRepository, PasswordEncoder passwordEncoder, FileHandleHelper fileHandleHelper, UserService userService, AttendanceRepository attendanceRepository, ExaminationService examinationService, ExamResultSummaryRepository examResultSummaryRepository) {
        this.repository = repository;
        this.academicStudentRepository = academicStudentRepository;
        this.passwordEncoder = passwordEncoder;
        this.fileHandleHelper = fileHandleHelper;
        this.userService = userService;
        this.attendanceRepository = attendanceRepository;
        this.examinationService = examinationService;
        this.examResultSummaryRepository = examResultSummaryRepository;
    }

    public List<Student> getAllActiveStudentsOfSchool(Long school_id) {
        return repository.findAllBySchool_IdAndStatusOrderByStudentNameAsc(school_id, "Active");
    }

    public List<Student> getAllInActiveStudents(Long school_id) {
        return repository.findAllBySchool_IdAndStatusOrderByStudentNameAsc(school_id, "Inactive");
    }

    public List<Student> getAllStudents(Long school_id) {
        return repository.findAllBySchool_IdOrderByStudentNameAsc(school_id);
    }

    public int getAllStudentsCount(Long school_id, Long academic_year_id) {
        return academicStudentRepository.countAllBySchool_IdAndAcademicYear_IdAndStatus(school_id, academic_year_id, "Active");
    }
    public int getAllInactiveStudentsCount(Long school_id, Long academic_year_id) {
        return academicStudentRepository.countAllBySchool_IdAndAcademicYear_IdAndStatus(school_id, academic_year_id, "Inactive");
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

    public List<Student> getAllActiveStudents(String status){
        return  repository.findAllByStatus(status);
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
                academicStudent.setStatus(AcademicStudent.STATUS_INACTIVE);
            }
            academicStudentRepository.saveAll(academicList);

            Student student = academicList.get(0).getStudent();

            student.setStatus(Student.STATUS_INACTIVE);
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

    public List getAttendanceDetailsByClass(Long school, Long academic){
        try{
            List<Object[]> results = attendanceRepository.findAttendanceSummaryBySchoolAndAcademicYear(school, academic);
            List<Map<String, Object>> summaries = new ArrayList<>();
            for (Object[] row : results) {
                Map<String, Object> summary = new HashMap<>();
                summary.put("mediumName", row[0]);        // Grade Name
                summary.put("gradeName", row[1]);        // Grade Name
                summary.put("sectionName", row[2]);     // Section Name
                summary.put("presentCount", row[3]);    // Present Count
                summary.put("absentCount", row[4]);     // Absent Count
                summaries.add(summary);
            }

            return summaries;

        }catch(Exception e){
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public Map getAllStudentsAttendanceByGrade(Long medium, Long gradeId, Long sectionId, Long academicYearId, Long schoolId){
        List<Attendance> attendanceList = attendanceRepository.findAllAttendanceSummaryForSchoolAndAcademicAndGrade(gradeId, sectionId, schoolId, academicYearId, medium);
        List<AcademicStudent> academicStudents = academicStudentRepository.findAllBySchool_IdAndMedium_IdAndGrade_IdAndSection_IdAndAcademicYear_IdAndStatus(schoolId, medium, gradeId, sectionId, academicYearId, "Active");
        Map<String, List> academicAttendanceMap = new HashMap<>();
        if(academicStudents!=null && !academicStudents.isEmpty()){
            academicAttendanceMap.put("academicStudents", academicStudents);
        }
        if(attendanceList!=null && !attendanceList.isEmpty()){
            academicAttendanceMap.put("attendances", attendanceList);
        }
        return academicAttendanceMap;
    }
    private Date truncateTime(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    @Transactional
    public String saveStudentsAttendance(List<Map<String, Object>> studentData, AcademicYear academic, School school){
        List<Attendance> studentsToSave = new ArrayList<>();
        int failCounter = 0;
        int passCounter = 0;
        try{
            Date truncatedDate = truncateTime(new Date());
            for (Map<String, Object> record : studentData){
                System.out.println("Record----"+record);
                boolean isChecked = (Boolean) record.get("isChecked");
                String remark = (String) record.get("remark");
                String uuid = (String) record.get("id");
                if(uuid!=null && !uuid.isEmpty()){
                    AcademicStudent academicStudent = academicStudentRepository.findByUuidAndStatusAndAcademicYear_IdAndSchool_Id(
                            UUID.fromString(uuid), "Active", academic.getId(), school.getId()).orElse(null);
                    if (academicStudent != null){
                        Attendance attendanceExist = attendanceRepository.findByAcademicStudentAndAttendanceDate(academicStudent, truncatedDate).orElse(null);
                        if(attendanceExist!=null){
                            if(attendanceExist.isPresent() != isChecked){
                                attendanceExist.setPresent(isChecked);
                                attendanceExist.setRemark(remark);
                                studentsToSave.add(attendanceExist);
                                passCounter++;
                            }
                        } else{
                            Attendance attendance = new Attendance();
                            attendance.setAttendanceDate(new Date());
                            attendance.setSchool(school);
                            attendance.setAcademicYear(academic);
                            attendance.setAcademicStudent(academicStudent);
                            attendance.setPresent(isChecked);
                            attendance.setRemark(remark);
                            studentsToSave.add(attendance);
                            passCounter++;
                        }
                    } else{
                        failCounter++;
                    }
                } else{
                    failCounter++;
                }
            }
            // Bulk save the students attendance
            attendanceRepository.saveAll(studentsToSave);
            return "Total Attendance captured: " + passCounter + " and Attendance not captured: "+failCounter;

        }catch(Exception e){
            e.printStackTrace();
            return "error#####"+e.getLocalizedMessage();
        }
    }

    public Date convertToDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
    public List<Map<String, Object>> getMonthlyAttendance(Long mediumId, Long gradeId, Long sectionId, Long schoolId, Long academicId, int month, int year){
        try{
            YearMonth yearMonth = YearMonth.of(year, month);
            LocalDate firstDay = yearMonth.atDay(1);
            LocalDate lastDay = yearMonth.atEndOfMonth();

            Date startDate = convertToDate(firstDay);
            Date endDate = convertToDate(lastDay);

            Set<Integer> sundays = new HashSet<>();
            for (LocalDate date = firstDay; !date.isAfter(lastDay); date = date.plusDays(1)) {
                if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    sundays.add(date.getDayOfMonth());
                }
            }
            List<AcademicStudent> students = academicStudentRepository.findAllBySchool_IdAndMedium_IdAndGrade_IdAndSection_IdAndAcademicYear_IdAndStatus(schoolId, mediumId, gradeId, sectionId, academicId, "Active");

            List<Attendance> attendanceRecords = attendanceRepository.findByAcademicStudentInAndAttendanceDateBetween(
                    students, startDate, endDate
            );



            Map<UUID, List<Attendance>> attendanceByStudent = attendanceRecords.stream()
                    .collect(Collectors.groupingBy(att -> att.getAcademicStudent().getUuid()));

            List<Map<String, Object>> attendanceList = new ArrayList<>();

            for (AcademicStudent student : students) {
                Map<String, Object> studentAttendance = new LinkedHashMap<>();

                // Add student details
                studentAttendance.put("studentId", student.getUuid().toString());
                studentAttendance.put("studentName", student.getStudent().getStudentName());
                studentAttendance.put("studentObj", student);

                // Initialize all dates as "A" (Absent) except Sundays
                for (int i = 1; i <= lastDay.getDayOfMonth(); i++) {
                    if (!sundays.contains(i)) {  // Exclude Sundays
                        studentAttendance.put(String.valueOf(i), "A"); // Default: Absent
                    } else{
                        studentAttendance.put(String.valueOf(i), "S");
                    }
                }

                // Populate attendance data
                if (attendanceByStudent.containsKey(student.getUuid())) {
                    for (Attendance attendance : attendanceByStudent.get(student.getUuid())) {
                        int day = attendance.getAttendanceDate().toInstant()
                                .atZone(java.time.ZoneId.systemDefault()).toLocalDate().getDayOfMonth();
                        if (!sundays.contains(day)) { // Exclude Sundays
                            studentAttendance.put(String.valueOf(day), attendance.isPresent() ? "P" : "A");
                        } else{
                            studentAttendance.put(String.valueOf(day), "S");
                        }
                    }
                }

                attendanceList.add(studentAttendance);
            }
            return attendanceList;
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public void getAttendanceSummaryByDates(Date startDate, Date endDate, Long schoolId, Long academicId, Long medium, Long gradeId, Long sectionId){
        List<Object[]> results = attendanceRepository.fetchAttendanceSummaryByDate(startDate, endDate, schoolId, gradeId, sectionId, academicId, medium);
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MMM/yyyy");
        if(results!=null && !results.isEmpty()){
            for (Object[] row : results) {
                Date attendanceDate = (Date) row[0];
                Long presentCount = (Long) row[1];
                Long absentCount = (Long) row[2];

                System.out.println("Date: " + sdf.format(attendanceDate) +
                        " | Present: " + presentCount +
                        " | Absent: " + absentCount);
            }
        }
    }

    @Transactional
    public String uploadAadhar(List<Map<String, String>> srdata, Long academic, Long school){
        int SRFailCounter = 0, srPassCounter = 0;
        List<AcademicStudent> studentsToSave = new ArrayList<>();
        List<Student> students = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();
        try{
            for (Map<String, String> rowData : srdata) {
                if (rowData.containsKey("Aadhar") && rowData.get("Aadhar")!=null && !rowData.get("Aadhar").isEmpty()) {
                    String uuid = rowData.get("ID#");
                    if (uuid != null && !uuid.isEmpty()) {
                        AcademicStudent academicStudent = academicStudentRepository.findByUuidAndStatusAndAcademicYear_IdAndSchool_Id(
                                UUID.fromString(uuid), "Active", academic, school).orElse(null);
                        Student studentObj = academicStudent.getStudent();
                        if (studentObj != null) {
                            studentObj.setAadharNo(rowData.get("SR"));
                            students.add(studentObj);  // Collect the student for bulk saving
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
            repository.saveAll(students);
            if(SRFailCounter == srdata.size()){
                return "error#####No aadhar found for update!";
            }
            return "Total Aadhar updated: " + srPassCounter + " and Aadhar not found for: "+SRFailCounter;
        }catch(Exception e){
            e.printStackTrace();
            return "error#####"+e.getLocalizedMessage();
        }
    }

    @Transactional
    public String uploadAadharFromTable(Map<String, String> studentData, Long academic, Long school){
        AtomicInteger SRFailCounter = new AtomicInteger();
        AtomicInteger srPassCounter = new AtomicInteger();
        List<Student> studentsToSave = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();
        try{
            studentData.forEach((key, value) -> {
                if(key!=null && value!=null && value!=""){
                    String uuid = key.split("sr_")[1];
                    if (uuid != null && !uuid.isEmpty()) {
                        AcademicStudent academicStudent = academicStudentRepository.findByUuidAndStatusAndAcademicYear_IdAndSchool_Id(
                                UUID.fromString(uuid), "Active", academic, school).orElse(null);
                        Student studentObj = academicStudent.getStudent();
                        if (studentObj != null) {
                            if(!value.equalsIgnoreCase(studentObj.getAadharNo().trim())){
                                if(value.length()==12){
                                    studentObj.setAadharNo(value);
                                    studentsToSave.add(studentObj);
                                    // Collect the student for bulk saving
                                    srPassCounter.getAndIncrement();
                                } else{
                                    SRFailCounter.getAndIncrement();
                                }
                            } else{
                                SRFailCounter.getAndIncrement();
                            }
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
            repository.saveAll(studentsToSave);
            if(SRFailCounter.get() == studentData.size()){
                return "error#####No aadhar found for update!";
            }
            return "success#####Total Aadhar updated: " + srPassCounter + " and Aadhar not found for: "+SRFailCounter;
        }catch (TransactionSystemException ex) {
            Throwable rootCause = ex.getRootCause();
            if (rootCause instanceof ConstraintViolationException) {
                ConstraintViolationException cve = (ConstraintViolationException) rootCause;
                for (ConstraintViolation<?> violation : cve.getConstraintViolations()) {
                    String propertyPath = violation.getPropertyPath().toString();
                    String message = violation.getMessage();
                    if ("aadharNo".equals(propertyPath) && "Aadhar number must be a 12-digit number".equals(message)) {
                        System.out.println("Validation error: " + message);
                        // Handle the error as needed
                    }
                }
            } else {
                // Handle other types of exceptions
                ex.printStackTrace();
                System.out.println("------------------------------");
            }
            return "---------";
        }catch(Exception e){
            e.printStackTrace();
            return "error#####"+e.getLocalizedMessage();
        }
    }

    public Map getAllStudentsOfActiveSession(Map<String, String> paramsMap, School school, AcademicYear academicYear){
        Map responseMap  = new HashMap();
        try{
            Map<String, Object> finalDataMap = new HashMap<>();
            if(paramsMap!=null && !paramsMap.isEmpty()){
                System.out.println("paramsMap:: "+paramsMap);
                String medium = paramsMap.get("medium");

                List<AcademicStudent> totalStudentCollectionDetails = academicStudentRepository.findAllStudentsDetailsBySession(school.getId(), academicYear.getId(), Long.parseLong(medium));
                finalDataMap.put("totalStudentCollectionDetails", (CollectionUtils.isEmpty(totalStudentCollectionDetails))? "No students details found for Medium": totalStudentCollectionDetails);
            }
            responseMap.put("finalData", finalDataMap);
        }catch(Exception e){
            e.printStackTrace();
            responseMap.put("error", e.getLocalizedMessage());
        }
        return responseMap;
    }

    public Map getAllStudentsOfActiveSessionGrades(Map<String, String> paramsMap, School school, AcademicYear academicYear){
        Map responseMap  = new HashMap();
        try{
            Map<String, Object> finalDataMap = new HashMap<>();
            if(paramsMap!=null && !paramsMap.isEmpty()){
                System.out.println("paramsMap:: "+paramsMap);
                String medium = paramsMap.get("medium");
                String section = paramsMap.get("section");
                String grade = paramsMap.get("grade");

                List<AcademicStudent> totalStudentCollectionDetails = academicStudentRepository.findAllBySchool_IdAndMedium_IdAndGrade_IdAndSection_IdAndAcademicYear_IdAndStatus(school.getId(),
                        Long.parseLong(medium), Long.parseLong(grade), Long.parseLong(section), academicYear.getId(), "Active");
                finalDataMap.put("totalStudentCollectionDetails", (CollectionUtils.isEmpty(totalStudentCollectionDetails))? "No students details found for selected Grade": totalStudentCollectionDetails);
            }
            responseMap.put("finalData", finalDataMap);
        }catch(Exception e){
            e.printStackTrace();
            responseMap.put("error", e.getLocalizedMessage());
        }
        return responseMap;
    }

    @Transactional
    public String uploadExamResult(List<Map<String, String>> srdata, AcademicYear academic, School school){
        int erFailCounter = 0, erPassCounter = 0;
        List<ExamResultSummary> studentsResultsToSave = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();
        try{
            List<String> requiredFields = Arrays.asList(
                    "Exam Name",
                    "Exam Result Date",
                    "Total Marks",
                    "Obtained Marks",
                    "Percentage(%)",
                    "Division",
                    "Result"
            );
            boolean supportSingleExam = true;
            String examName = "";
            //validating - exam should be unique
            for (Map<String, String> rowData : srdata) {
                if(examName.trim().length()>0){
                    if(!examName.equalsIgnoreCase(rowData.get("Exam Name"))){
                        supportSingleExam = false;
                        break;
                    }
                } else{
                    examName = rowData.get("Exam Name");
                }
            }
            if(supportSingleExam){
                ExamDetails examDetails = examinationService.getExamDetailByName(examName, academic.getId(), school.getId());
                SimpleDateFormat sf = new SimpleDateFormat("dd/MMM/yyyy");
                School schoolObj = null;
                if(examDetails!=null){
                    for (Map<String, String> rowData : srdata) {
                        List<String> missingFields = new ArrayList<>();

                        for (String field : requiredFields) {
                            String value = rowData.get(field);
                            if (value == null || value.trim().isEmpty()) {
                                missingFields.add(field);
                            }
                        }

                        if (missingFields.isEmpty()) {
                            String uuid = rowData.get("ID#");
                            if (uuid != null && !uuid.isEmpty()) {
                                AcademicStudent academicStudent = academicStudentRepository.findByUuidAndStatusAndAcademicYear_IdAndSchool_Id(
                                        UUID.fromString(uuid), "Active", academic.getId(), school.getId()).orElse(null);

                                if (academicStudent != null) {
                                    ExamResultSummary examResultSummary = new ExamResultSummary();
                                    examResultSummary.setResult(rowData.get("Result"));
                                    examResultSummary.setExamDetails(examDetails);
                                    examResultSummary.setExamResultDate(sf.parse(rowData.get("Exam Result Date")));
                                    examResultSummary.setAcademicStudent(academicStudent);
                                    examResultSummary.setSchool(school);
                                    examResultSummary.setAcademicYear(academic);
                                    examResultSummary.setDivision(rowData.get("Division"));
                                    examResultSummary.setObtainedMarks(Long.parseLong(rowData.get("Obtained Marks")));
                                    examResultSummary.setTotalMarks(Long.parseLong(rowData.get("Total Marks")));
                                    examResultSummary.setPercentageMarks(Double.parseDouble(rowData.get("Percentage(%)")));
                                    examResultSummary.setRemarks(rowData.get("remarks"));
                                    examResultSummary.setCreatedBy(userService.getLoggedInUser().getUsername());
                                    examResultSummary.setUpdatedBy(userService.getLoggedInUser().getUsername());

                                    studentsResultsToSave.add(examResultSummary);  // Collect the student for bulk saving
                                    erPassCounter++;
                                } else {
                                    failedIds.add(uuid);  // Log the failure
                                    erFailCounter++;
                                }
                            } else {
                                failedIds.add("Invalid UUID");
                                erFailCounter++;
                            }
                        } else {
                            erFailCounter++;
                        }
                    }
                    // Bulk save the students
                    examResultSummaryRepository.saveAll(studentsResultsToSave);
                    return "Total Results updated: " + erPassCounter + " and Results not found for: "+erFailCounter;
                } else{
                    return "error#####Examination name not found. Kindly check your examination name!";
                }
            } else{
                return "error#####Different exam results found, please re-check!";
            }

        }catch(Exception e){
            e.printStackTrace();
            return "error#####"+e.getLocalizedMessage();
        }
    }

    public List<ExamResultSummary> getExamResultsForStudents(Long medium, Long grade, Long section, Long exam, Long academic, Long school){
        ExamDetails examDetails = examinationService.getExamDetailById(exam, academic, school);
        List<ExamResultSummary> examResultSummaries = examResultSummaryRepository.getExamResultSummariesBy(school, academic, medium, grade, section, examDetails);
        System.out.println(examResultSummaries);
        return examResultSummaries;
    }

    public List getAttendanceDetailsCollectedByClass(Long school, Long academic){
        try{
            List<Object[]> results = attendanceRepository.findAttendanceCollectedSummaryBySchoolAndAcademicYear(school, academic);
            List<Map<String, Object>> summaries = new ArrayList<>();
            for (Object[] row : results) {
                System.out.println("row"+row);
                Map<String, Object> summary = new HashMap<>();
                summary.put("mediumName", row[0]);        // Grade Name
                summary.put("gradeName", row[1]);        // Grade Name
                summary.put("presentCount", row[2]);    // Present Count
                summary.put("absentCount", row[3]);     // Absent Count
                summaries.add(summary);
            }
            return summaries;

        }catch(Exception e){
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public Map getPieChartData(Long school, Long academic, int totalActiveStudents){
        try{
            Map<String, Object> chartData = new HashMap<>();
            //1. Total Active Student, 2. Total SR, 3. Total Aadhaar, 4. Total Absent, 5. Total Boys/Girls Absent, 6. Total Boys/Girls
            int girlsCount = academicStudentRepository.countAllBySchool_IdAndAcademicYear_IdAndStatusAndStudent_Gender(school, academic, "Active","Female");
            int boysCount = academicStudentRepository.countAllBySchool_IdAndAcademicYear_IdAndStatusAndStudent_Gender(school, academic, "Active","Male");
            int noPreferenceCount = totalActiveStudents - (boysCount + girlsCount);

            int aadharCount = academicStudentRepository.countWhereAadharNoIsPresent(school, academic, "Active");
            int srCount = academicStudentRepository.countAllBySchool_IdAndAcademicYear_IdAndStatusAndClassSrNoIsNotNull(school, academic, "Active");
            int totalStudentPresentToday = attendanceRepository.countAllBySchool_IdAndAcademicYear_IdAndAcademicStudent_StatusAndIsPresentAndAttendanceDate(school, academic, "Active", true, new Date());
            System.out.println("girlsCount: "+girlsCount+" aadharCount: "+aadharCount+" srCount: "+srCount);
            chartData.put("totalAadhaarCount", aadharCount);
            chartData.put("totalSRCount", srCount);
            chartData.put("totalAbsentCount", (totalActiveStudents-totalStudentPresentToday));
            chartData.put("totalGirlsCount", girlsCount);
            chartData.put("totalBoysCount", boysCount);
            chartData.put("totalNPCount", noPreferenceCount);

            //Counting gender-wise
            int girlsPresentCount = attendanceRepository.countPresentStudentsByGenderToday(school, academic, "Active", "Female");
            int boysPresentCount = attendanceRepository.countPresentStudentsByGenderToday(school, academic, "Active", "Male");
            int noPreferencePresentCount = attendanceRepository.countPresentStudentsByGenderToday(school, academic, "Active", "No_Preference");

            chartData.put("totalGirlsAbsent", (girlsCount-girlsPresentCount));
            chartData.put("totalBoysAbsent", (boysCount-boysPresentCount));
            chartData.put("totalNPAbsent", (noPreferenceCount-noPreferencePresentCount));

            return chartData;
        }catch(Exception e){
            e.printStackTrace();
        }
        return new HashMap();
    }

    private String getStudentCountByGender(Long school, Long academic, int totalActiveStudents){
        try{
            int boyCount = academicStudentRepository.countAllBySchool_IdAndAcademicYear_IdAndStatusAndStudent_Gender(school, academic, "Active","Male");
            int girlCount = academicStudentRepository.countAllBySchool_IdAndAcademicYear_IdAndStatusAndStudent_Gender(school, academic, "Active","Female");
            int otherCount = totalActiveStudents - (boyCount + girlCount);
            return "BSC_"+boyCount+"###GSC_"+girlCount+"###NPSC_"+otherCount;
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public List<String[]> getComingBirthDays(Long school, Long academic){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MMM/yyyy");
        List<String[]> dataList = new ArrayList<>();
        try{
            List<Object[]> stuDobList = academicStudentRepository.findUpcomingBirthdaysInNext7Days(school, academic, "Active");
            if(!stuDobList.isEmpty()){
                for(Object[] dd:stuDobList){
                    LocalDate dob = ((Date) dd[0]).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    String formattedDob = dob.format(formatter);
                    String studentName = (String) dd[1];
                    System.out.println("DOB: "+dd[0]+" Name: "+dd[1]);
                    String[] dobList = new String[2];
                    dobList[1] = studentName;
                    dobList[0] = formattedDob;
                    dataList.add(dobList);
                }
            }
            //Student Data added
            return dataList;
        }catch(Exception e){
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public List getAbsentSummaryGradewise(Long school, Long academic){
        try{
            boolean hasAny = attendanceRepository.existsAnyAttendanceForToday(school, academic)>0;
            if(!hasAny){
                return new ArrayList<>();
            }
            List<Object[]> results = academicStudentRepository.getGradeWiseAttendanceSummary(school, academic);
            List<Map<String, Object>> summaries = new ArrayList<>();
            if(!results.isEmpty()){
                for (Object[] row : results) {
                    Map<String, Object> summary = new HashMap<>();
                    /*String className = (String) row[0];
                    Long total = ((Number) row[1]).longValue();
                    Long present = ((Number) row[2]).longValue();
                    Long absent = ((Number) row[3]).longValue();*/
                    summary.put("absentSummaryCount", row[3]);     // Absent Count
                    summary.put("presentSummaryCount", row[2]);     // Present Count
                    summary.put("totalSummaryCount", row[1]);     // Total Count
                    summary.put("gradeNameSummary", row[0]);     // Grade Name
                    summaries.add(summary);
                    //System.out.println(className + " → Total: " + total + ", Present: " + present + ", Absent: " + absent);
                }
                return summaries;
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return new ArrayList<>();
    }
}
