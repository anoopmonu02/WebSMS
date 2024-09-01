package com.smsweb.sms.controllers.student;

import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.services.globalaccess.ExcelService;
import com.smsweb.sms.services.student.StudentService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                Long mediumId = Long.parseLong(requestBody.getOrDefault("mediumId", "0"));
                Long gradeId = Long.parseLong(requestBody.getOrDefault("gradeId", "0"));
                Long sectionId = Long.parseLong(requestBody.getOrDefault("sectionId", "0"));

                Map<String, Object> responseMap = excelService.downloadSampleSRExcel(gradeId, sectionId, mediumId, 14L, 4L);
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
}
