package com.smsweb.sms.controllers.student;

import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.services.globalaccess.ExcelService;
import com.smsweb.sms.services.student.StudentService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
public class StudentRestController {
    private final ExcelService excelService;
    private final StudentService studentService;

    public StudentRestController(ExcelService excelService, StudentService studentService) {
        this.excelService = excelService;
        this.studentService = studentService;
    }

    @PostMapping("/downloadSRSampleFile")
    public ResponseEntity<?> downloadSRSampleFile(@RequestBody Map<String, String> requestBody) throws IOException {
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

                Map<String, Object> responseMap = excelService.downloadSampleSRExcel(gradeId, sectionId, mediumId, 14L, 4L, fileType);
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
    public ResponseEntity<?> fetchStudentForSR(@RequestBody Map<String, String> requestBody){
        try{
            if(requestBody!=null){
                Long mediumId = Long.parseLong(requestBody.getOrDefault("mediumId", "0"));
                Long gradeId = Long.parseLong(requestBody.getOrDefault("gradeId", "0"));
                Long sectionId = Long.parseLong(requestBody.getOrDefault("sectionId", "0"));
                List<AcademicStudent> academicStudents = studentService.getAllStudentsByGrade(mediumId, gradeId, sectionId, 14L, 4L);
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
                for (Map.Entry<String, List<String[]>> innerEntry : innerMap.entrySet()) {
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
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }

        return ResponseEntity.ok(dataMap);
    }

    @PostMapping("/upload-sr-data")
    public ResponseEntity<?> uploadSRData(@RequestBody List<Map<String, String>> tableData){
        String responseMsg = "";
        try{
            System.out.println("Received data: " + tableData);
            responseMsg = studentService.uploadSR(tableData, 14L);
        }catch(Exception e){
            e.printStackTrace();
        }
        return ResponseEntity.ok(responseMsg);
    }

    @PostMapping("/getStudentsForSR")
    public ResponseEntity<?> getStudentsForSR(@RequestBody Map<String, String> requestBody){
        try{
            if(requestBody!=null){
                String medium = requestBody.getOrDefault("mediumId","0");
                String grade = requestBody.getOrDefault("gradeId","0");
                String section = requestBody.getOrDefault("sectionId","0");
                Long mediumId = (medium!=null && medium!="")?Long.parseLong(medium):0L;
                Long gradeId = (grade!=null && grade!="")?Long.parseLong(grade):0L;
                Long sectionId = (section!=null && section!="")?Long.parseLong(section):0L;
                List<AcademicStudent> academicStudents = studentService.getAllStudentsByGrade(mediumId, gradeId, sectionId, 14L, 4L);
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
    public ResponseEntity<?> saveStudentSRFromTable(@RequestBody Map<String, String> studentData){
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
                String responseMsg = studentService.uploadSRFromTable(studentData, 14L);
                return ResponseEntity.ok(responseMsg);
            }
        }catch(Exception e){
            e.printStackTrace();
            return ResponseEntity.ok("error#####"+e.getLocalizedMessage());
        }
    }

}
