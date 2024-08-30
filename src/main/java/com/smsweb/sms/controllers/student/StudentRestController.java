package com.smsweb.sms.controllers.student;

import com.smsweb.sms.services.globalaccess.ExcelService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class StudentRestController {
    private final ExcelService excelService;

    public StudentRestController(ExcelService excelService) {
        this.excelService = excelService;
    }

    @PostMapping("/downloadSRSampleFile")
    public ResponseEntity<?> downloadSRSampleFile(@RequestBody Map<String, String> requestBody){
        Map result = new HashMap<>();
        try{
            System.out.println("requestBody--------> "+requestBody);
            if(requestBody!=null){
                Long mediumId = requestBody.get("mediumId")!=null?Long.parseLong(requestBody.get("mediumId")):0L;
                Long gradeId = requestBody.get("gradeId")!=null?Long.parseLong(requestBody.get("gradeId")):0L;
                Long sectionId = requestBody.get("sectionId")!=null?Long.parseLong(requestBody.get("sectionId")):0L;
                String retrunMsg = excelService.downloadSampleSRExcel(gradeId, sectionId, mediumId, 14L, 4L);
                if(retrunMsg!=null && retrunMsg.startsWith("error")){
                    result.put("error", retrunMsg);
                }
                if(retrunMsg!=null && retrunMsg.startsWith("success")){
                    result.put("success", retrunMsg);
                }
                System.out.println("responseMap "+result);
                //System.out.println("result "+result.keySet());
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return ResponseEntity.ok(result);
    }
}
