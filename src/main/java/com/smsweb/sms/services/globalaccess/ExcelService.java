package com.smsweb.sms.services.globalaccess;

import com.smsweb.sms.helper.ExcelFileHandler;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.universal.Grade;
import com.smsweb.sms.models.universal.Medium;
import com.smsweb.sms.models.universal.Section;
import com.smsweb.sms.services.student.AcademicStudentService;
import com.smsweb.sms.services.universal.GradeService;
import com.smsweb.sms.services.universal.MediumService;
import com.smsweb.sms.services.universal.SectionService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExcelService {
    private final GradeService gradeService;
    private final MediumService mediumService;
    private final SectionService sectionService;
    private final AcademicStudentService academicStudentService;

    public ExcelService(GradeService gradeService, MediumService mediumService, SectionService sectionService, AcademicStudentService academicStudentService) {
        this.gradeService = gradeService;
        this.mediumService = mediumService;
        this.sectionService = sectionService;
        this.academicStudentService = academicStudentService;
    }


    public Map<String, Object> downloadSampleSRExcel(Long grade, Long section, Long medium, Long academic, Long school) {
        Map<String, Object> responseMap = new HashMap<>();
        try {
            Grade gradeObj = gradeService.getGradeById(grade).orElse(null);
            Section secObj = sectionService.getSectionById(section).orElse(null);
            Medium mediumObj = mediumService.getMediumById(medium).orElse(null);
            List<AcademicStudent> academicStudentList = academicStudentService.getAllAcademicStudentByGrade(medium, grade, section, academic, school);

            if (gradeObj == null) {
                responseMap.put("error", "Grade not found");
                return responseMap;
            }
            if (secObj == null) {
                responseMap.put("error", "Section not found");
                return responseMap;
            }
            if (mediumObj == null) {
                responseMap.put("error", "Medium not found");
                return responseMap;
            }
            if (academicStudentList == null || academicStudentList.isEmpty()) {
                responseMap.put("error", "No student found");
                return responseMap;
            }

            String[] mediumGradeSection = {
                    mediumObj.getMediumName(),
                    gradeObj.getGradeName(),
                    secObj.getSectionName()
            };

            // Generate and download the Excel file
            ByteArrayInputStream excelFile = new ExcelFileHandler().LoadSampleSRFile("Academic_Students_SR_Sample_File.xlsx", academicStudentList, mediumGradeSection);
            responseMap.put("filecreated", excelFile);
            return responseMap;
        } catch (Exception e) {
            e.printStackTrace();
            responseMap.put("error", e.getLocalizedMessage());
            return responseMap;
        }
    }

}
