package com.smsweb.sms.controllers.student;

import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.services.globalaccess.ExcelService;
import com.smsweb.sms.services.student.AcademicStudentService;
import com.smsweb.sms.services.student.StudentService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
public class StudentRestController extends BaseController {
    private final ExcelService excelService;
    private final StudentService studentService;
    private final AcademicStudentService academicStudentService;

    public StudentRestController(ExcelService excelService, StudentService studentService, AcademicStudentService academicStudentService) {
        this.excelService = excelService;
        this.studentService = studentService;
        this.academicStudentService = academicStudentService;
    }

    @PostMapping("/downloadSRSampleFile")
    public ResponseEntity<?> downloadSRSampleFile(@RequestBody Map<String, String> requestBody, Model model) throws IOException {
        String fileName = "Academic_Students_SR_Sample_File.xlsx";
        try{
            System.out.println("requestBody--------> "+requestBody);
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
                Map<String, Object> responseMap = excelService.downloadSampleSRExcel(gradeId, sectionId, mediumId, academicYear.getId(), school.getId(), fileType);
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

    @GetMapping("/fetchStudentForSR")
    public ResponseEntity<?> fetchStudentForSR(@RequestBody Map<String, String> requestBody, Model model){
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

    @PostMapping("/upload-sr-file")
    public ResponseEntity<?> validateExcelDataForSR(@RequestParam("file") MultipartFile file){
        Map<String, Map<String, List<String[]>>> excelData = excelService.checkAndValidateSRData(file);
        Map<String, List<String[]>> dataMap = new HashMap<>();
        try{

            for (Map.Entry<String, Map<String, List<String[]>>> outerEntry : excelData.entrySet()) {
                String outerKey = outerEntry.getKey();
                Map<String, List<String[]>> innerMap = outerEntry.getValue();

                System.out.println("Outer Key: " + outerKey);
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

                    System.out.println("\tInner Key: " + innerKey);

                    // Iterate through the list of String arrays
                    for (String[] stringArray : stringList) {
                        System.out.print("\t\tValues: ");
                        for (String str : stringArray) {
                            System.out.print(str + " ");
                        }
                        System.out.println();
                    }
                }*/
            }
        }catch(Exception e){
            e.printStackTrace();
        }

        return ResponseEntity.ok(dataMap);
    }

    @PostMapping("/upload-sr-data")
    public ResponseEntity<?> uploadSRData(@RequestBody List<Map<String, String>> tableData, Model model){
        String responseMsg = "";
        try{
            System.out.println("Received data: " + tableData);
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
            responseMsg = studentService.uploadSR(tableData, academicYear.getId(), school.getId());
        }catch(Exception e){
            e.printStackTrace();
        }
        return ResponseEntity.ok(responseMsg);
    }

    @PostMapping("/getStudentsForSR")
    public ResponseEntity<?> getStudentsForSR(@RequestBody Map<String, String> requestBody, Model model){
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
                return ResponseEntity.ok(academicStudents);
            } else{
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Request body is missing or invalid.");
            }
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + e.getMessage());
        }
    }

    @PostMapping("/saveStudentSRFromTable")
    public ResponseEntity<?> saveStudentSRFromTable(@RequestBody Map<String, String> studentData, Model model){
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
    @GetMapping("/getStudentDetail/{uuid}")
    public ResponseEntity<?> getStudentDetail(@PathVariable("uuid") String uuid, Model model){
        School school = (School)model.getAttribute("school");
        AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
        AcademicStudent students = academicStudentService.getStudentDetailByUuid(UUID.fromString(uuid), academicYear.getId(), school.getId()).orElse(null);
        return ResponseEntity.ok(students);
    }

    @PostMapping("/updateStudentGradeSection")
    public ResponseEntity<?> updateStudentGradeOrSection(@RequestBody Map<String, String> studentData, Model model){
        try{
            System.out.println("student Data>>>>>>>>>>>> "+studentData);
            //mediumId=1, gradeId=5, sectionId=1, stuId=bfe37aab-d8fe-4481-af94-ae5d871f2ce5, reason=
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
            String responseMsg = academicStudentService.updateGradeSection(studentData, academicYear.getId(), school.getId());
            return ResponseEntity.ok(responseMsg);
        }catch(Exception e){
            e.printStackTrace();
            return ResponseEntity.ok("error#####"+e.getLocalizedMessage());
        }
    }

    @PostMapping("/getStudentsForAttendance")
    public ResponseEntity<?> getStudentsForAttendance(@RequestBody Map<String, String> requestBody, Model model){
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
                    if(!academicAttendaceMap.containsKey("academicStudents")){
                        academicAttendaceMap.put("academicStudentError", "No students found for the given criteria.");
                    }
                } else {
                    academicAttendaceMap = new HashMap();
                    academicAttendaceMap.put("academicStudentError", "No students found for the given criteria.");
                }
                return ResponseEntity.ok(academicAttendaceMap);
            } else{
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Request body is missing or invalid.");
            }
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred: " + e.getMessage());
        }
    }

    @PostMapping("/saveStudentAttendance")
    public ResponseEntity<?> saveStudentsAttendance(@RequestBody List<Map<String, Object>> studentData, Model model){
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

    @PostMapping("/getStudentsMonthlyAttendance")
    public ResponseEntity<?> getStudentsMonthlyAttendance(@RequestBody Map<String, String> requestBody, Model model){
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

    @PostMapping("/downloadAadharSampleFile")
    public ResponseEntity<?> downloadAadharSampleFile(@RequestBody Map<String, String> requestBody, Model model) throws IOException {
        String fileName = "Academic_Students_Aadhar_Sample_File.xlsx";
        try{
            System.out.println("requestBody--------> "+requestBody);
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

    @PostMapping("/upload-aadhar-file")
    public ResponseEntity<?> validateExcelDataForAadhar(@RequestParam("file") MultipartFile file){
        Map<String, Map<String, List<String[]>>> excelData = excelService.checkAndValidateAadharData(file);
        Map<String, List<String[]>> dataMap = new HashMap<>();
        try{

            for (Map.Entry<String, Map<String, List<String[]>>> outerEntry : excelData.entrySet()) {
                String outerKey = outerEntry.getKey();
                Map<String, List<String[]>> innerMap = outerEntry.getValue();

                System.out.println("Outer Key: " + outerKey);
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

    @PostMapping("/upload-aadhar-data")
    public ResponseEntity<?> uploadAadharData(@RequestBody List<Map<String, String>> tableData, Model model){
        String responseMsg = "";
        try{
            System.out.println("Received data: " + tableData);
            School school = (School)model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear)model.getAttribute("academicYear");
            responseMsg = studentService.uploadAadhar(tableData, academicYear.getId(), school.getId());
        }catch(Exception e){
            e.printStackTrace();
        }
        return ResponseEntity.ok(responseMsg);
    }

    @PostMapping("/saveStudentAadharFromTable")
    public ResponseEntity<?> saveStudentAadharFromTable(@RequestBody Map<String, String> studentData, Model model){
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
}
