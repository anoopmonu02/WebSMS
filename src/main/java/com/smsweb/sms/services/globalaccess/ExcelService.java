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

import java.util.List;

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


    public String downloadSampleSRExcel(Long grade, Long section, Long medium, Long academic, Long school) {
        try {
            Grade gradeObj = gradeService.getGradeById(grade).orElse(null);
            Section secObj = sectionService.getSectionById(section).orElse(null);
            Medium mediumObj = mediumService.getMediumById(medium).orElse(null);
            List<AcademicStudent> academicStudentList = academicStudentService.getAllAcademicStudentByGrade(medium, grade, section, academic, school);

            if (gradeObj == null) {
                return "error:Grade not found";
            }
            if (secObj == null) {
                return "error:Section not found";
            }
            if (mediumObj == null) {
                return "error:Medium not found";
            }
            if (academicStudentList == null || academicStudentList.isEmpty()) {
                return "error:No student found";
            }

            String[] mediumGradeSection = {
                    mediumObj.getMediumName(),
                    gradeObj.getGradeName(),
                    secObj.getSectionName()
            };

            // Generate and download the Excel file
            String resultMessage = new ExcelFileHandler().LoadSampleSRFile("Academic_Students_SR_Sample_File.xlsx", academicStudentList, mediumGradeSection);

            if ("success".equalsIgnoreCase(resultMessage)) {
                return "success:File Downloaded";
            } else {
                return "error:Error in downloading file.";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "error:" + e.getLocalizedMessage();
        }
    }

}
