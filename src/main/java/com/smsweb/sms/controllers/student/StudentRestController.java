package com.smsweb.sms.controllers.student;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.ExamResultSummary;
import com.smsweb.sms.models.student.StudentDiscount;
import com.smsweb.sms.models.student.StudentRegionalDetail;
import com.smsweb.sms.helper.GradeWiseImageDownloadHelper;
import com.smsweb.sms.helper.BoardRegistrationHelper;
import com.smsweb.sms.repositories.student.StudentRegionalDetailRepository;
import com.smsweb.sms.services.globalaccess.ExcelService;
import com.smsweb.sms.services.student.AcademicStudentService;
import com.smsweb.sms.services.student.StudentDiscountService;
import com.smsweb.sms.services.student.StudentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@RestController
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_ACCOUNTENT','ROLE_STAFF')")
public class StudentRestController extends BaseController {
    private static final Logger log = LoggerFactory.getLogger(StudentRestController.class);

    private final ExcelService excelService;
    private final StudentService studentService;
    private final AcademicStudentService academicStudentService;
    private final StudentDiscountService studentDiscountService;
    private final GradeWiseImageDownloadHelper gradeWiseImageDownloadHelper;
    private final BoardRegistrationHelper boardRegistrationHelper;
    private final StudentRegionalDetailRepository studentRegionalDetailRepository;

    @Value("${student.image.storage.path}")
    private String studentImageDirectory;

    public StudentRestController(ExcelService excelService, StudentService studentService, AcademicStudentService academicStudentService, StudentDiscountService studentDiscountService, GradeWiseImageDownloadHelper gradeWiseImageDownloadHelper, BoardRegistrationHelper boardRegistrationHelper, StudentRegionalDetailRepository studentRegionalDetailRepository) {
        this.excelService = excelService;
        this.studentService = studentService;
        this.academicStudentService = academicStudentService;
        this.studentDiscountService = studentDiscountService;
        this.gradeWiseImageDownloadHelper = gradeWiseImageDownloadHelper;
        this.boardRegistrationHelper = boardRegistrationHelper;
        this.studentRegionalDetailRepository = studentRegionalDetailRepository;
    }

    @CheckAccess(screen = "STUDENT_ASSIGN_SR", type = AccessType.VIEW)
    @PostMapping("/downloadSRSampleFile")
    public ResponseEntity<?> downloadSRSampleFile(@RequestBody Map<String, String> requestBody, Model model) throws IOException {
        log.info("Inside downloadSRSampleFile");
        String fileName = "Academic_Students_SR_Sample_File.xlsx";
        try{
            log.debug("requestBody={}", requestBody);
            if(requestBody!=null){
                String medium = requestBody.getOrDefault("mediumId","0");
                String grade = requestBody.getOrDefault("gradeId","0");
                String section = requestBody.getOrDefault("sectionId","0");
                Long mediumId = (medium!=null && medium!="")?Long.parseLong(medium):0;
                Long gradeId = (grade!=null && grade!="")?Long.parseLong(grade):0;
                Long sectionId = (section!=null && section!="")?Long.parseLong(section):0;
                String fileType = requestBody.getOrDefault("fileType", "");
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
                Map<String, Object> responseMap = excelService.downloadSampleSRExcel(gradeId, sectionId, mediumId, academicYear.getId(), school.getId(), fileType, "sr");
                if(responseMap!=null && responseMap.containsKey("error")){
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseMap);
                }
                if(responseMap!=null && responseMap.containsKey("filecreated")){
                    ByteArrayInputStream file = (ByteArrayInputStream)responseMap.get("filecreated");
                    InputStreamResource in = new InputStreamResource(file);

                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename="+fileName)
                            .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                            .body(in);
                }
            }
        }catch(Exception e){
            e.printStackTrace();
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getLocalizedMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
        Map<String, String> response = new HashMap<>();
        response.put("error", "Invalid request data");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @CheckAccess(screen = "STUDENT_ASSIGN_SR", type = AccessType.VIEW)
    @GetMapping("/fetchStudentForSR")
    public ResponseEntity<?> fetchStudentForSR(@RequestBody Map<String, String> requestBody, Model model){
        log.info("Inside fetchStudentForSR");
        try{
            if(requestBody!=null){
                Long mediumId = Long.parseLong(requestBody.getOrDefault("mediumId", "0"));
                Long gradeId = Long.parseLong(requestBody.getOrDefault("gradeId", "0"));
                Long sectionId = Long.parseLong(requestBody.getOrDefault("sectionId", "0"));
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
                List<AcademicStudent> academicStudents = studentService.getAllStudentsByGrade(mediumId, gradeId, sectionId, academicYear.getId(), school.getId());
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return ResponseEntity.ok(null);
    }

    @CheckAccess(screen = "STUDENT_ASSIGN_SR", type = AccessType.CREATE)
    @PostMapping("/upload-sr-file")
    public ResponseEntity<?> validateExcelDataForSR(@RequestParam("file") MultipartFile file){
        log.info("Inside validateExcelDataForSR");
        Map<String, Map<String, List<String[]>>> excelData = excelService.checkAndValidateSRData(file);
        Map<String, List<String[]>> dataMap = new HashMap<>();
        try{

            for (Map.Entry<String, Map<String, List<String[]>>> outerEntry : excelData.entrySet()) {
                String outerKey = outerEntry.getKey();
                Map<String, List<String[]>> innerMap = outerEntry.getValue();

                log.debug("Processing outer key: {}", outerKey);
                if("success".equalsIgnoreCase(outerKey)){
                    dataMap.put(outerKey, innerMap.get("DATA"));
                } else{
                    List<String[]> lst = new ArrayList<>();
                    String[] errdata = new String[1];
                    errdata[0] = innerMap.keySet().toArray()[0].toString();
                    lst.add(errdata);
                    dataMap.put(outerKey, lst);
                }
                //dataMap.put(outerKey)
                // Iterate through the inner map
                /*for (Map.Entry<String, List<String[]>> innerEntry : innerMap.entrySet()) {
                    String innerKey = innerEntry.getKey();
                    List<String[]> stringList = innerEntry.getValue();

                    // Iterate through the list of String arrays
                    for (String[] stringArray : stringList) {
                        System.out.print("\t\tValues: ");
                        for (String str : stringArray) {
                            System.out.print(str + " ");
                        }
                    }
                }*/
            }
        }catch(Exception e){
            e.printStackTrace();
        }

        return ResponseEntity.ok(dataMap);
    }

    @CheckAccess(screen = "STUDENT_ASSIGN_SR", type = AccessType.CREATE)
    @PostMapping("/upload-sr-data")
    public ResponseEntity<?> uploadSRData(@RequestBody List<Map<String, String>> tableData, Model model){
        log.info("Inside uploadSRData");
        String responseMsg = "";
        try{
            log.debug("Received data, size={}", tableData.size());
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
            responseMsg = studentService.uploadSR(tableData, academicYear.getId(), school.getId());
        }catch(Exception e){
            e.printStackTrace();
        }
        return ResponseEntity.ok(responseMsg);
    }

    @CheckAccess(screen = "STUDENT_ASSIGN_SR", type = AccessType.VIEW)
    @PostMapping("/getStudentsForSR")
    public ResponseEntity<?> getStudentsForSR(@RequestBody Map<String, String> requestBody, Model model){
        log.info("Inside getStudentsForSR");
        try{
            if(requestBody!=null){
                String medium = requestBody.getOrDefault("mediumId","0");
                String grade = requestBody.getOrDefault("gradeId","0");
                String section = requestBody.getOrDefault("sectionId","0");
                Long mediumId = (medium!=null && medium!="")?Long.parseLong(medium):0L;
                Long gradeId = (grade!=null && grade!="")?Long.parseLong(grade):0L;
                Long sectionId = (section!=null && section!="")?Long.parseLong(section):0L;
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
                List<AcademicStudent> academicStudents = studentService.getAllStudentsByGrade(mediumId, gradeId, sectionId, academicYear.getId(), school.getId());
                if(academicStudents == null || academicStudents.isEmpty()){
                    return ResponseEntity.ok("No students found for the given criteria.");
                }
                List<Map<String, Object>> leanList = new java.util.ArrayList<>();
                for (AcademicStudent as : academicStudents) {
                    leanList.add(studentService.toLeanAcademicStudentMap(as));
                }
                return ResponseEntity.ok(leanList);
            } else{
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Request body is missing or invalid.");
            }
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + e.getMessage());
        }
    }

    @CheckAccess(screen = "STUDENT_GRADEWISE_IMAGE_DOWNLOAD", type = AccessType.VIEW)
    @PostMapping("/downloadGradeWiseImages")
    public ResponseEntity<?> downloadGradeWiseImages(@RequestBody Map<String, String> requestBody, Model model){
        log.info("Inside downloadGradeWiseImages");
        try{
            if(requestBody == null){
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Request body is missing or invalid."));
            }
            String medium = requestBody.getOrDefault("mediumId","0");
            String grade = requestBody.getOrDefault("gradeId","0");
            String section = requestBody.getOrDefault("sectionId","0");
            Long mediumId = (medium!=null && !medium.isEmpty())?Long.parseLong(medium):0L;
            Long gradeId = (grade!=null && !grade.isEmpty())?Long.parseLong(grade):0L;
            Long sectionId = (section!=null && !section.isEmpty())?Long.parseLong(section):0L;
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");

            List<AcademicStudent> academicStudents = studentService.getAllStudentsByGrade(mediumId, gradeId, sectionId, academicYear.getId(), school.getId());
            if(academicStudents == null || academicStudents.isEmpty()){
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "No students found for the given criteria."));
            }

            String gradeName = academicStudents.get(0).getGrade().getGradeName();
            String sectionName = academicStudents.get(0).getSection().getSectionName();

            GradeWiseImageDownloadHelper.ZipBuildResult result = gradeWiseImageDownloadHelper.buildDownloadZip(academicStudents, gradeName, sectionName, studentImageDirectory);
            String zipFileName = gradeWiseImageDownloadHelper.buildZipFileName(gradeName, sectionName);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=\""+zipFileName+"\"")
                    .header("X-Total-Student-Count", String.valueOf(academicStudents.size()))
                    .header("X-With-Image-Count", String.valueOf(result.withImageCount))
                    .header("X-Without-Image-Count", String.valueOf(result.withoutImageCount))
                    .header("Access-Control-Expose-Headers", "X-Total-Student-Count, X-With-Image-Count, X-Without-Image-Count, Content-Disposition")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(result.zipBytes);
        }catch(Exception e){
            log.error("Error building grade-wise image download zip", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An unexpected error occurred: " + e.getMessage()));
        }
    }

    @CheckAccess(screen = "STUDENT_BOARD_REGISTRATION_CLASS9", type = AccessType.VIEW)
    @PostMapping("/previewBoardRegistrationClass9")
    public ResponseEntity<?> previewBoardRegistrationClass9(@RequestBody Map<String, String> requestBody, Model model){
        log.info("Inside previewBoardRegistrationClass9");
        try{
            List<Map<String, Object>> rows = buildBoardRegistrationRows(requestBody, model);
            return ResponseEntity.ok(rows);
        }catch(IllegalArgumentException iae){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", iae.getMessage()));
        }catch(Exception e){
            log.error("Error building board registration preview", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An unexpected error occurred: " + e.getMessage()));
        }
    }

    @CheckAccess(screen = "STUDENT_BOARD_REGISTRATION_CLASS9", type = AccessType.VIEW)
    @PostMapping("/exportBoardRegistrationClass9")
    public ResponseEntity<?> exportBoardRegistrationClass9(@RequestBody Map<String, String> requestBody, Model model){
        log.info("Inside exportBoardRegistrationClass9");
        try{
            List<Map<String, Object>> rows = buildBoardRegistrationRows(requestBody, model);
            byte[] excelBytes = boardRegistrationHelper.buildExcel(rows);
            String fileName = "BoardRegistration_Class9_" + new SimpleDateFormat("ddMMyyyy").format(new Date()) + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=\""+fileName+"\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excelBytes);
        }catch(IllegalArgumentException iae){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", iae.getMessage()));
        }catch(Exception e){
            log.error("Error building board registration export", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An unexpected error occurred: " + e.getMessage()));
        }
    }

    /**
     * Shared by both the preview and export endpoints so they can never disagree: same students,
     * same field mapping, same order. gradeId is expected to already be Grade 9's resolved id,
     * passed through from the page (which resolved it once in StudentController).
     */
    private List<Map<String, Object>> buildBoardRegistrationRows(Map<String, String> requestBody, Model model) {
        if (requestBody == null) {
            throw new IllegalArgumentException("Request body is missing or invalid.");
        }
        String medium = requestBody.getOrDefault("mediumId", "0");
        String gradeIdStr = requestBody.getOrDefault("gradeId", "0");
        String section = requestBody.getOrDefault("sectionId", "0");
        Long mediumId = (medium != null && !medium.isEmpty()) ? Long.parseLong(medium) : 0L;
        Long gradeId = (gradeIdStr != null && !gradeIdStr.isEmpty()) ? Long.parseLong(gradeIdStr) : 0L;
        Long sectionId = (section != null && !section.isEmpty()) ? Long.parseLong(section) : 0L;

        if (gradeId == 0L) {
            throw new IllegalArgumentException("Grade 9 could not be resolved. Check that the Grade master has a record named exactly \"9\".");
        }

        School school = (School) model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");

        List<AcademicStudent> academicStudents = studentService.getAllStudentsByGrade(mediumId, gradeId, sectionId, academicYear.getId(), school.getId());
        if (academicStudents == null || academicStudents.isEmpty()) {
            throw new IllegalArgumentException("No students found for the given criteria.");
        }

        Map<Long, StudentRegionalDetail> regionalByStudentId = new HashMap<>();
        for (AcademicStudent as : academicStudents) {
            if (as.getStudent() != null) {
                studentRegionalDetailRepository.findByStudent_Id(as.getStudent().getId())
                        .ifPresent(rd -> regionalByStudentId.put(as.getStudent().getId(), rd));
            }
        }

        return boardRegistrationHelper.buildRows(academicStudents, regionalByStudentId);
    }

    @CheckAccess(screen = "STUDENT_ASSIGN_SR", type = AccessType.CREATE)
    @PostMapping("/saveStudentSRFromTable")
    public ResponseEntity<?> saveStudentSRFromTable(@RequestBody Map<String, String> studentData, Model model){
        log.info("Inside saveStudentSRFromTable");
        try{
            AtomicInteger counter = new AtomicInteger();
            studentData.forEach((key, value) -> {
                if(value==null || value.trim()==""){
                    counter.getAndIncrement();
                }
            });
            if(counter.intValue() == studentData.size()){
                return ResponseEntity.ok("error#####No SR found for students.");
            } else{
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
                String responseMsg = studentService.uploadSRFromTable(studentData, academicYear.getId(), school.getId());
                return ResponseEntity.ok(responseMsg);
            }
        }catch(Exception e){
            e.printStackTrace();
            return ResponseEntity.ok("error#####"+e.getLocalizedMessage());
        }
    }
    //getStudentDetail
    @CheckAccess(screen = "STUDENT_VIEW", type = AccessType.VIEW)
    @GetMapping("/getStudentDetail/{uuid}")
    public ResponseEntity<?> getStudentDetail(@PathVariable("uuid") String uuid, Model model){
        log.info("Inside getStudentDetail");
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
        AcademicStudent students = academicStudentService.getStudentDetailByUuid(UUID.fromString(uuid), academicYear.getId(), school.getId()).orElse(null);
        if (students == null) {
            return ResponseEntity.ok(java.util.Map.of("noAcademicStudent", "Student not found."));
        }
        return ResponseEntity.ok(studentService.toLeanAcademicStudentMap(students));
    }

    @CheckAccess(screen = "STUDENT_EDIT_GRADE", type = AccessType.EDIT)
    @PostMapping("/updateStudentGradeSection")
    public ResponseEntity<?> updateStudentGradeOrSection(@RequestBody Map<String, String> studentData, Model model){
        log.info("Inside updateStudentGradeOrSection");
        try{
            log.debug("updateStudentGradeOrSection studentData={}", studentData);
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
            String responseMsg = academicStudentService.updateGradeSection(studentData, academicYear.getId(), school.getId());
            return ResponseEntity.ok(responseMsg);
        }catch(Exception e){
            e.printStackTrace();
            return ResponseEntity.ok("error#####"+e.getLocalizedMessage());
        }
    }

    @CheckAccess(screen = "STUDENT_ATTENDANCE_MARK", type = AccessType.VIEW)
    @PostMapping("/getStudentsForAttendance")
    public ResponseEntity<?> getStudentsForAttendance(@RequestBody Map<String, String> requestBody, Model model){
        log.info("Inside getStudentsForAttendance");
        try{
            if(requestBody!=null){
                String medium = requestBody.getOrDefault("mediumId","0");
                String grade = requestBody.getOrDefault("gradeId","0");
                String section = requestBody.getOrDefault("sectionId","0");
                Long mediumId = (medium!=null && medium!="")?Long.parseLong(medium):0L;
                Long gradeId = (grade!=null && grade!="")?Long.parseLong(grade):0L;
                Long sectionId = (section!=null && section!="")?Long.parseLong(section):0L;
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
                //List<AcademicStudent> academicStudents = studentService.getAllStudentsByGrade(mediumId, gradeId, sectionId, academicYear.getId(), school.getId());
                Map academicAttendaceMap = studentService.getAllStudentsAttendanceByGrade(mediumId, gradeId, sectionId, academicYear.getId(), school.getId());
                if(academicAttendaceMap!=null && !academicAttendaceMap.isEmpty()){
                    try{
                        if(!academicAttendaceMap.containsKey("academicStudents")){
                            academicAttendaceMap.put("academicStudentError", "No students found for the given criteria.");
                        }
                    }catch(Exception ee){
                        ee.printStackTrace();
                    }

                } else {
                    academicAttendaceMap = new HashMap();
                    academicAttendaceMap.put("academicStudentError", "No students found for the given criteria.");
                }
                return ResponseEntity.ok(academicAttendaceMap);
            } else{
                Map<String, String> error = new HashMap<>();
                error.put("error", "Request body is missing or invalid.");
                return ResponseEntity.badRequest().body(error);
            }
        }catch(Exception e){
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + e.getMessage());
        }
    }

    @CheckAccess(screen = "STUDENT_ATTENDANCE_MARK", type = AccessType.CREATE)
    @PostMapping("/saveStudentAttendance")
    public ResponseEntity<?> saveStudentsAttendance(@RequestBody List<Map<String, Object>> studentData, Model model){
        log.info("Inside saveStudentsAttendance");
        try{
            if(studentData.size()==0){
                return ResponseEntity.ok("error#####No attendance found for students.");
            } else{
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
                String responseMsg = studentService.saveStudentsAttendance(studentData, academicYear, school);
                return ResponseEntity.ok(responseMsg);
            }
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * Confirms today's attendance for the current school/academic-year so it's safe to
     * publish to parents in the mobile app (once MobileAttendanceController is wired to
     * respect this flag). Gated by its own STUDENT_ATTENDANCE_CONFIRM screen — deliberately
     * separate from STUDENT_ATTENDANCE_MARK — so it can be granted to a specific assigned
     * user independent of who's allowed to mark attendance day-to-day.
     */
    @CheckAccess(screen = "STUDENT_ATTENDANCE_CONFIRM", type = AccessType.EDIT)
    @PostMapping("/confirmTodaysAttendance")
    public ResponseEntity<?> confirmTodaysAttendance(Model model){
        log.info("Inside confirmTodaysAttendance");
        try{
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
            Map<String, Object> result = studentService.confirmTodaysAttendance(school, academicYear);
            return ResponseEntity.ok(result);
        }catch(Exception e){
            log.error("Failed to confirm today's attendance", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Confirm failed: " + e.getMessage()));
        }
    }

    @CheckAccess(screen = "STUDENT_ATTENDANCE_REPORT", type = AccessType.VIEW)
    @PostMapping("/getStudentsMonthlyAttendance")
    public ResponseEntity<?> getStudentsMonthlyAttendance(@RequestBody Map<String, String> requestBody, Model model){
        log.info("Inside getStudentsMonthlyAttendance");
        try{
            if(requestBody!=null){
                String medium = requestBody.getOrDefault("mediumId","0");
                String grade = requestBody.getOrDefault("gradeId","0");
                String section = requestBody.getOrDefault("sectionId","0");
                String monthVal = requestBody.getOrDefault("monthId","0");
                Long mediumId = (medium!=null && medium!="")?Long.parseLong(medium):0L;
                Long gradeId = (grade!=null && grade!="")?Long.parseLong(grade):0L;
                Long sectionId = (section!=null && section!="")?Long.parseLong(section):0L;
                int month = (monthVal!=null && monthVal!="")?Integer.parseInt(monthVal):0;
                Calendar calendar = Calendar.getInstance();
                int year = calendar.get(Calendar.YEAR);
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
                List<Map<String, Object>> stuAttData = studentService.getMonthlyAttendance(mediumId, gradeId, sectionId, school.getId(), academicYear.getId(), month, year);

                if(stuAttData==null || stuAttData.isEmpty()){
                    Map studentAttendance = new HashMap();
                    studentAttendance.put("academicStudentError", "No students found for the given criteria.");
                    stuAttData.add(studentAttendance);
                }
                return ResponseEntity.ok(stuAttData);
            } else{
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Request body is missing or invalid.");
            }
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + e.getMessage());
        }
    }

    @CheckAccess(screen = "STUDENT_EDIT_AADHAR", type = AccessType.VIEW)
    @PostMapping("/downloadAadharSampleFile")
    public ResponseEntity<?> downloadAadharSampleFile(@RequestBody Map<String, String> requestBody, Model model) throws IOException {
        log.info("Inside downloadAadharSampleFile");
        String fileName = "Academic_Students_Aadhar_Sample_File.xlsx";
        try{
            log.debug("requestBody={}", requestBody);
            if(requestBody!=null){
                String medium = requestBody.getOrDefault("mediumId","0");
                String grade = requestBody.getOrDefault("gradeId","0");
                String section = requestBody.getOrDefault("sectionId","0");
                Long mediumId = (medium!=null && medium!="")?Long.parseLong(medium):0;
                Long gradeId = (grade!=null && grade!="")?Long.parseLong(grade):0;
                Long sectionId = (section!=null && section!="")?Long.parseLong(section):0;
                String fileType = requestBody.getOrDefault("fileType", "");
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
                Map<String, Object> responseMap = excelService.downloadSampleAadharExcel(gradeId, sectionId, mediumId, academicYear.getId(), school.getId(), fileType);
                if(responseMap!=null && responseMap.containsKey("error")){
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseMap);
                }
                if(responseMap!=null && responseMap.containsKey("filecreated")){
                    ByteArrayInputStream file = (ByteArrayInputStream)responseMap.get("filecreated");
                    InputStreamResource in = new InputStreamResource(file);

                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename="+fileName)
                            .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                            .body(in);
                }
            }
        }catch(Exception e){
            e.printStackTrace();
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getLocalizedMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
        Map<String, String> response = new HashMap<>();
        response.put("error", "Invalid request data");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @CheckAccess(screen = "STUDENT_EDIT_AADHAR", type = AccessType.EDIT)
    @PostMapping("/upload-aadhar-file")
    public ResponseEntity<?> validateExcelDataForAadhar(@RequestParam("file") MultipartFile file){
        log.info("Inside validateExcelDataForAadhar");
        Map<String, Map<String, List<String[]>>> excelData = excelService.checkAndValidateAadharData(file);
        Map<String, List<String[]>> dataMap = new HashMap<>();
        try{

            for (Map.Entry<String, Map<String, List<String[]>>> outerEntry : excelData.entrySet()) {
                String outerKey = outerEntry.getKey();
                Map<String, List<String[]>> innerMap = outerEntry.getValue();

                log.debug("Processing outer key: {}", outerKey);
                if("success".equalsIgnoreCase(outerKey)){
                    dataMap.put(outerKey, innerMap.get("DATA"));
                } else{
                    List<String[]> lst = new ArrayList<>();
                    String[] errdata = new String[1];
                    errdata[0] = innerMap.keySet().toArray()[0].toString();
                    lst.add(errdata);
                    dataMap.put(outerKey, lst);
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }

        return ResponseEntity.ok(dataMap);
    }

    @CheckAccess(screen = "STUDENT_EDIT_AADHAR", type = AccessType.EDIT)
    @PostMapping("/upload-aadhar-data")
    public ResponseEntity<?> uploadAadharData(@RequestBody List<Map<String, String>> tableData, Model model){
        log.info("Inside uploadAadharData");
        String responseMsg = "";
        try{
            log.debug("Received data, size={}", tableData.size());
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
            responseMsg = studentService.uploadAadhar(tableData, academicYear.getId(), school.getId());
        }catch(Exception e){
            e.printStackTrace();
        }
        return ResponseEntity.ok(responseMsg);
    }

    @CheckAccess(screen = "STUDENT_EDIT_AADHAR", type = AccessType.EDIT)
    @PostMapping("/saveStudentAadharFromTable")
    public ResponseEntity<?> saveStudentAadharFromTable(@RequestBody Map<String, String> studentData, Model model){
        log.info("Inside saveStudentAadharFromTable");
        try{
            AtomicInteger counter = new AtomicInteger();
            studentData.forEach((key, value) -> {
                if(value==null || value.trim()==""){
                    counter.getAndIncrement();
                }
            });
            if(counter.intValue() == studentData.size()){
                return ResponseEntity.ok("error#####No Aadhar found for students.");
            } else{
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
                String responseMsg = studentService.uploadAadharFromTable(studentData, academicYear.getId(), school.getId());
                return ResponseEntity.ok(responseMsg);
            }
        }catch(Exception e){
            e.printStackTrace();
            return ResponseEntity.ok("error#####"+e.getLocalizedMessage());
        }
    }

    @CheckAccess(screen = "STUDENT_REPORT_SESSION", type = AccessType.VIEW)
    @PostMapping("/getTotalStudentDetails")
    public ResponseEntity<?> getTotalStudentDetails(@RequestBody Map<String, String> requestBody, Model model){
        log.info("Inside getTotalStudentDetails");
        Map<String, Object> receiptData = new HashMap<>();
        try{
            log.debug("requestBody={}", requestBody);
            if(requestBody!=null){
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
                Map result = studentService.getAllStudentsOfActiveSession(requestBody, school, academicYear);
                return ResponseEntity.ok(result);
            }
        }catch(Exception e){
            receiptData.put("error","error#####"+e.getLocalizedMessage());
            e.printStackTrace();
        }

        return ResponseEntity.ok(receiptData);
    }

    @CheckAccess(screen = "STUDENT_REPORT_GRADE", type = AccessType.VIEW)
    @PostMapping("/getTotalStudentDetailsByGrade")
    public ResponseEntity<?> getTotalStudentDetailsByGrade(@RequestBody Map<String, String> requestBody, Model model){
        log.info("Inside getTotalStudentDetailsByGrade");
        Map<String, Object> receiptData = new HashMap<>();
        try{
            log.debug("requestBody={}", requestBody);
            if(requestBody!=null){
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
                Map result = studentService.getAllStudentsOfActiveSessionGrades(requestBody, school, academicYear);
                return ResponseEntity.ok(result);
            }
        }catch(Exception e){
            receiptData.put("error","error#####"+e.getLocalizedMessage());
            e.printStackTrace();
        }

        return ResponseEntity.ok(receiptData);
    }

    @CheckAccess(screen = "STUDENT_ID_CARD", type = AccessType.VIEW)
    @PostMapping("/getStudentsForIdCard")
    public ResponseEntity<?> getStudentsForIdCard(@RequestBody Map<String, String> requestBody, Model model) {
        log.info("Inside getStudentsForIdCard");
        try {
            if (requestBody != null) {
                Long mediumId  = Long.parseLong(requestBody.getOrDefault("medium",  "0"));
                Long gradeId   = Long.parseLong(requestBody.getOrDefault("grade",   "0"));
                Long sectionId = Long.parseLong(requestBody.getOrDefault("section", "0"));
                School school = (School) model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
                if (school == null || academicYear == null) {
                    return ResponseEntity.badRequest().body(Map.of("error", "School or academic year not found in session"));
                }
                List<AcademicStudent> students = studentService.getAllStudentsByGrade(
                        mediumId, gradeId, sectionId, academicYear.getId(), school.getId());

                // Map to a flat DTO — returning the full entity graph serializes School,
                // AcademicYear, UserEntity and all their relations (~2 MB), which causes
                // JSON.parse to fail on the client. We only send what the ID card needs.
                List<Map<String, Object>> result = students.stream().map(as -> {
                    Map<String, Object> stuMap = new HashMap<>();
                    stuMap.put("studentName", as.getStudent().getStudentName());
                    stuMap.put("fatherName",  as.getStudent().getFatherName());
                    stuMap.put("motherName",  as.getStudent().getMotherName());
                    stuMap.put("dob",         as.getStudent().getDob());
                    stuMap.put("address",     as.getStudent().getAddress());
                    stuMap.put("landmark",    as.getStudent().getLandmark());
                    stuMap.put("mobile1",     as.getStudent().getMobile1());
                    stuMap.put("pic",         as.getStudent().getPic());
                    stuMap.put("classSrNo",   as.getClassSrNo());
                    stuMap.put("gradeName",   as.getGrade()   != null ? as.getGrade().getGradeName()     : "");
                    stuMap.put("sectionName", as.getSection() != null ? as.getSection().getSectionName() : "");
                    return stuMap;
                }).collect(java.util.stream.Collectors.toList());

                return ResponseEntity.ok(result);
            }
        } catch (Exception e) {
            log.error("Error fetching students for ID card", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getLocalizedMessage()));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "Invalid request"));
    }

    @CheckAccess(screen = "STUDENT_EXAM_RESULT", type = AccessType.VIEW)
    @PostMapping("/downloadSampleFileToEnterExamResult")
    public ResponseEntity<?> downloadSampleFileToEnterExamResult(@RequestBody Map<String, String> requestBody, Model model) throws IOException {
        log.info("Inside downloadSampleFileToEnterExamResult");
        String fileName = "Academic_Students_Exam_Result_Sample_File.xlsx";
        try{
            log.debug("requestBody={}", requestBody);
            if(requestBody!=null){
                String medium = requestBody.getOrDefault("mediumId","0");
                String grade = requestBody.getOrDefault("gradeId","0");
                String section = requestBody.getOrDefault("sectionId","0");
                Long mediumId = (medium!=null && medium!="")?Long.parseLong(medium):0;
                Long gradeId = (grade!=null && grade!="")?Long.parseLong(grade):0;
                Long sectionId = (section!=null && section!="")?Long.parseLong(section):0;
                String fileType = requestBody.getOrDefault("fileType", "");
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
                Map<String, Object> responseMap = excelService.downloadSampleSRExcel(gradeId, sectionId, mediumId, academicYear.getId(), school.getId(), fileType, "exam");
                if(responseMap!=null && responseMap.containsKey("error")){
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(responseMap);
                }
                if(responseMap!=null && responseMap.containsKey("filecreated")){
                    ByteArrayInputStream file = (ByteArrayInputStream)responseMap.get("filecreated");
                    InputStreamResource in = new InputStreamResource(file);

                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename="+fileName)
                            .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                            .body(in);
                }
            }
        }catch(Exception e){
            e.printStackTrace();
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getLocalizedMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
        Map<String, String> response = new HashMap<>();
        response.put("error", "Invalid request data");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @CheckAccess(screen = "STUDENT_EXAM_RESULT", type = AccessType.EDIT)
    @PostMapping("/upload-exam-result-file")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_TEACHER','ROLE_ACCOUNTENT','ROLE_STAFF')")
    public ResponseEntity<?> validateExcelDataForExamResult(@RequestParam("file") MultipartFile file){
        log.info("Inside validateExcelDataForExamResult");
        Map<String, Map<String, List<String[]>>> excelData = excelService.checkAndValidateExamResultData(file);
        Map<String, List<String[]>> dataMap = new HashMap<>();
        try{
            for (Map.Entry<String, Map<String, List<String[]>>> outerEntry : excelData.entrySet()) {
                String outerKey = outerEntry.getKey();
                Map<String, List<String[]>> innerMap = outerEntry.getValue();

                log.debug("Processing outer key: {}", outerKey);
                if("success".equalsIgnoreCase(outerKey)){
                    dataMap.put(outerKey, innerMap.get("DATA"));
                } else{
                    List<String[]> lst = new ArrayList<>();
                    String[] errdata = new String[1];
                    errdata[0] = innerMap.keySet().toArray()[0].toString();
                    lst.add(errdata);
                    dataMap.put(outerKey, lst);
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }

        return ResponseEntity.ok(dataMap);
    }

    @CheckAccess(screen = "STUDENT_EXAM_RESULT", type = AccessType.EDIT)
    @PostMapping("/upload-exam-result-data")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_TEACHER','ROLE_ACCOUNTENT','ROLE_STAFF')")
    public ResponseEntity<?> uploadExamResultData(@RequestBody List<Map<String, String>> tableData, Model model){
        log.info("Inside uploadExamResultData");
        String responseMsg = "";
        try{
            log.debug("Received data, size={}", tableData.size());
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
            responseMsg = studentService.uploadExamResult(tableData, academicYear, school);
        }catch(Exception e){
            e.printStackTrace();
        }
        return ResponseEntity.ok(responseMsg);
    }

    @CheckAccess(screen = "STUDENT_EXAM_RESULT", type = AccessType.VIEW)
    @PostMapping("/getStudentsExamResults")
    public ResponseEntity<?> getSelectedGradeStudentsExamResult(@RequestBody Map<String, String> tableData, Model model){
        log.info("Inside getSelectedGradeStudentsExamResult");
        String responseMsg = "";
        try{
            if(tableData!=null && !tableData.isEmpty()){
                log.debug("Received data, size={}", tableData.size());
                String medium = tableData.getOrDefault("mediumId","0");
                String grade = tableData.getOrDefault("gradeId","0");
                String section = tableData.getOrDefault("sectionId","0");
                String examId = tableData.getOrDefault("examId","0");
                Long mediumId = (medium!=null && medium!="")?Long.parseLong(medium):0L;
                Long gradeId = (grade!=null && grade!="")?Long.parseLong(grade):0L;
                Long sectionId = (section!=null && section!="")?Long.parseLong(section):0L;
                Long exam = (examId!=null && examId!="")?Long.parseLong(examId):0L;
                School school = (School)model.getAttribute("school");
                AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
                List<ExamResultSummary> examResultSummaries = studentService.getExamResultsForStudents(mediumId, gradeId, sectionId,exam, academicYear.getId(), school.getId());
                if(examResultSummaries == null || examResultSummaries.isEmpty()){
                    return ResponseEntity.ok(java.util.Map.of("empty", true, "message", "No result found for the given criteria."));
                }
                List<Map<String, Object>> leanResults = new java.util.ArrayList<>();
                for (ExamResultSummary ers : examResultSummaries) {
                    Map<String, Object> ersMap = new java.util.HashMap<>();
                    ersMap.put("id", ers.getId());
                    ersMap.put("examResultDate", ers.getExamResultDate());
                    ersMap.put("totalMarks", ers.getTotalMarks());
                    ersMap.put("obtainedMarks", ers.getObtainedMarks());
                    ersMap.put("percentageMarks", ers.getPercentageMarks());
                    ersMap.put("division", ers.getDivision() != null ? ers.getDivision() : "");
                    ersMap.put("result", ers.getResult() != null ? ers.getResult() : "");
                    ersMap.put("remarks", ers.getRemarks() != null ? ers.getRemarks() : "");
                    if (ers.getAcademicStudent() != null) {
                        ersMap.put("academicStudent", studentService.toLeanAcademicStudentMap(ers.getAcademicStudent()));
                    }
                    if (ers.getExamDetails() != null && ers.getExamDetails().getExamination() != null) {
                        ersMap.put("examDetails", java.util.Map.of(
                            "id", ers.getExamDetails().getId(),
                            "examination", java.util.Map.of(
                                "examinationName", ers.getExamDetails().getExamination().getExaminationName() != null ? ers.getExamDetails().getExamination().getExaminationName() : ""
                            )
                        ));
                    }
                    leanResults.add(ersMap);
                }
                return ResponseEntity.ok(leanResults);
            } else{
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Request body is missing or invalid.");
            }

        }catch(Exception e){
            e.printStackTrace();
            return ResponseEntity.ok(java.util.Map.of("empty", true, "message", "An error occurred: " + e.getMessage()));
        }
        //return ResponseEntity.ok(java.util.Map.of("empty", true, "message", "No result found for the given criteria."));
    }

    //searchStudentData
    @CheckAccess(screen = "STUDENT_SEARCH", type = AccessType.VIEW)
    @PostMapping("/searchStudentData")
    public ResponseEntity<?> searchStudentData(@RequestBody Map<String, String> requestBody, Model model){
        log.info("Inside searchStudentData");
        School school = (School)model.getAttribute("school");
        List<Map<String, Object>> leanList = new ArrayList<>();
        try{
            if(requestBody!=null){
                String academicId = requestBody.getOrDefault("academic_year","0");
                String query = requestBody.getOrDefault("query","No Data");
                List<AcademicStudent> rawList = academicStudentService.searchStudentsAll(query, academicId, school.getId());
                if (rawList != null) {
                    for (AcademicStudent as : rawList) leanList.add(studentService.toLeanAcademicStudentMap(as));
                }
            } else{
                throw new IllegalArgumentException("request is not valid");
            }
        }catch(Exception e){
            log.error("searchStudentData failed", e);
        }
        return ResponseEntity.ok(leanList);
    }

    @CheckAccess(screen = "STUDENT_DISCOUNT_LIST", type = AccessType.VIEW)
    @PostMapping("/getStudentsDiscountList")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_TEACHER','ROLE_ACCOUNTENT','ROLE_STAFF')")
    @ResponseBody
    public ResponseEntity<?> getStudentsDiscountList(@RequestBody Map<String, String> requestBody, Model model){
        log.info("Inside getStudentsDiscountList");
        School school = (School)model.getAttribute("school");
        List<Map<String, String>> studentDataList;
        Map responseMap = new HashMap<>();
        try {
            if(requestBody!=null){
                String academicId = requestBody.getOrDefault("academicYearId","0");
                Long acId = 0L;
                try{
                    if(academicId!=null){
                       acId = Long.parseLong(academicId);
                    }
                    studentDataList = studentDiscountService.getAllStudentDiscountsBySession(school.getId(), acId);
                    responseMap.put("stuData", studentDataList);
                }catch(Exception ex){
                    responseMap.put("error", ex.getLocalizedMessage());
                }
            } else{
                throw new IllegalArgumentException("request is not valid");
            }
        }catch (Exception e){
            responseMap.put("error", e.getLocalizedMessage());
        }
        return ResponseEntity.ok(responseMap);
    }
}
