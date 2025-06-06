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
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class ExcelService {
    private final GradeService gradeService;
    private final MediumService mediumService;
    private final SectionService sectionService;
    private final AcademicStudentService academicStudentService;
    private final ExcelFileHandler excelFileHandler;

    public ExcelService(GradeService gradeService, MediumService mediumService, SectionService sectionService, AcademicStudentService academicStudentService, ExcelFileHandler excelFileHandler) {
        this.gradeService = gradeService;
        this.mediumService = mediumService;
        this.sectionService = sectionService;
        this.academicStudentService = academicStudentService;
        this.excelFileHandler = excelFileHandler;
    }


    public Map<String, Object> downloadSampleSRExcel(Long grade, Long section, Long medium, Long academic, Long school, String fileType, String calledFrom) {
        Map<String, Object> responseMap = new HashMap<>();
        try {
            Grade gradeObj = gradeService.getGradeById(grade).orElse(null);
            Section secObj = sectionService.getSectionById(section).orElse(null);
            Medium mediumObj = mediumService.getMediumById(medium).orElse(null);
            List<AcademicStudent> academicStudentList;
            if("F".equalsIgnoreCase(fileType)){
                academicStudentList = academicStudentService.getAllAcademicStudent(academic, school);
            } else{
                academicStudentList = academicStudentService.getAllAcademicStudentByGrade(medium, grade, section, academic, school);
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
            }

            if (academicStudentList == null || academicStudentList.isEmpty()) {
                responseMap.put("error", "No student found");
                return responseMap;
            }

            String[] mediumGradeSection;
            if("F".equalsIgnoreCase(fileType)){
                mediumGradeSection = new String[]{};
            } else{
                mediumGradeSection = new String[]{
                        mediumObj.getMediumName(),
                        gradeObj.getGradeName(),
                        secObj.getSectionName()
                };
            }

            // Generate and download the Excel file
            ByteArrayInputStream excelFile;
            if("exam".equalsIgnoreCase(calledFrom)){
                excelFile = excelFileHandler.LoadSampleSRFile("G_marks_entry", academicStudentList, mediumGradeSection, fileType);
            } else{
                excelFile = excelFileHandler.LoadSampleSRFile("sr_file", academicStudentList, mediumGradeSection, fileType);
            }
            responseMap.put("filecreated", excelFile);
            return responseMap;
        } catch (Exception e) {
            e.printStackTrace();
            responseMap.put("error", e.getLocalizedMessage());
            return responseMap;
        }
    }

    public Map<String, Map<String, List<String[]>>> checkAndValidateSRData(MultipartFile excelFile){
        String msg = "";
        Map<String, Map<String, List<String[]>>> validatedData = new HashMap<>();
        Map<String, List<String[]>> childData = new HashMap<>();
        try{
            boolean isValidFile = excelFileHandler.checkValidExcelFormat(excelFile);
            if(!isValidFile){
                childData.put("File format not supported or not valid", null);
                validatedData.put("error", childData);
                return validatedData;
            }
            List<String[]> excelData = excelFileHandler.excelDataToList(excelFile.getInputStream(), 2);
            if(excelData==null || excelData.isEmpty()){
                childData.put("Data not found or not valid", null);
                validatedData.put("error", childData);
                return validatedData;
            }

            //Read Data
            excelData = readSRExcelDataAndValidate(excelData, "sr_file");
            if(excelData==null || excelData.isEmpty()){
                childData.put("Unable to read data", null);
                validatedData.put("error", childData);
                return validatedData;
            }
            childData.put("DATA", excelData);
            validatedData.put("success", childData);
            System.out.println("excelData "+excelData.size());
            return validatedData;

        }catch(Exception e){
            e.printStackTrace();
            childData.put(e.getLocalizedMessage(), null);
            validatedData.put("error", childData);
            return validatedData;
        }
    }

    public List<String[]> readSRExcelDataAndValidate(List<String[]> excelData, String fileName){
        List<String[]> validatedData = new ArrayList<>();
        try{
            for(String[] rowData : excelData){
                if("exam_file".equalsIgnoreCase(fileName)){
                    //if called from exam result
                    //rowData[6-12]
                    if (rowData.length < 15) {
                        rowData = Arrays.copyOf(rowData, 15);
                    }
                    boolean hasMissing = false;
                    for (int i = 6; i <= 12; i++) {
                        if (i >= rowData.length || rowData[i] == null || rowData[i].trim().isEmpty()) {
                            hasMissing = true;
                            break;
                        }
                    }
                    if(rowData[7]!=null && rowData[7].trim()!=""){
                        rowData[7] = parseAndFormatDate(rowData[7]);
                    }
                    System.out.println("Row Data[7] "+ rowData[7]);
                    System.out.println("Row Data[7] "+ rowData[8]);
                    System.out.println("Row Data[7] "+ rowData[9]);
                    System.out.println("Row Data[7] "+ rowData[10]);
                    if (hasMissing) {
                        rowData[14] = "error#####Failed: Mandatory fields for exam result are missing.";
                    } else {
                        rowData[14] = "success#####Passed";
                    }
                } else{
                    if (rowData.length < 7) {
                        rowData = Arrays.copyOf(rowData, 7);
                    }
                    if(rowData[5]==null || rowData[5].trim().isEmpty()){
                        if("aadhar_file".equalsIgnoreCase(fileName)){
                            rowData[6] = "error#####Failed: Aadhar No required";
                        } else{
                            rowData[6] = "error#####Failed: SR No required";
                        }
                    } else{
                        rowData[6] = "success#####Passed";
                    }
                }
                validatedData.add(rowData);
            }
            return validatedData;
        }catch(Exception e){
            e.printStackTrace();
        }
        return validatedData;
    }

    public Map<String, Object> downloadSampleAadharExcel(Long grade, Long section, Long medium, Long academic, Long school, String fileType) {
        Map<String, Object> responseMap = new HashMap<>();
        try {
            Grade gradeObj = gradeService.getGradeById(grade).orElse(null);
            Section secObj = sectionService.getSectionById(section).orElse(null);
            Medium mediumObj = mediumService.getMediumById(medium).orElse(null);
            List<AcademicStudent> academicStudentList;
            if("F".equalsIgnoreCase(fileType)){
                academicStudentList = academicStudentService.getAllAcademicStudent(academic, school);
            } else{
                academicStudentList = academicStudentService.getAllAcademicStudentByGrade(medium, grade, section, academic, school);
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
            }

            if (academicStudentList == null || academicStudentList.isEmpty()) {
                responseMap.put("error", "No student found");
                return responseMap;
            }

            String[] mediumGradeSection;
            if("F".equalsIgnoreCase(fileType)){
                mediumGradeSection = new String[]{};
            } else{
                mediumGradeSection = new String[]{
                        mediumObj.getMediumName(),
                        gradeObj.getGradeName(),
                        secObj.getSectionName()
                };
            }

            // Generate and download the Excel file
            ByteArrayInputStream excelFile = excelFileHandler.LoadSampleSRFile("aadhar_file", academicStudentList, mediumGradeSection, fileType);
            responseMap.put("filecreated", excelFile);
            return responseMap;
        } catch (Exception e) {
            e.printStackTrace();
            responseMap.put("error", e.getLocalizedMessage());
            return responseMap;
        }
    }

    public Map<String, Map<String, List<String[]>>> checkAndValidateAadharData(MultipartFile excelFile){
        String msg = "";
        Map<String, Map<String, List<String[]>>> validatedData = new HashMap<>();
        Map<String, List<String[]>> childData = new HashMap<>();
        try{
            boolean isValidFile = excelFileHandler.checkValidExcelFormat(excelFile);
            if(!isValidFile){
                childData.put("File format not supported or not valid", null);
                validatedData.put("error", childData);
                return validatedData;
            }
            List<String[]> excelData = excelFileHandler.excelDataToList(excelFile.getInputStream(), 2);
            if(excelData==null || excelData.isEmpty()){
                childData.put("Data not found or not valid", null);
                validatedData.put("error", childData);
                return validatedData;
            }

            //Read Data
            excelData = readSRExcelDataAndValidate(excelData, "aadhar_file");
            if(excelData==null || excelData.isEmpty()){
                childData.put("Unable to read data", null);
                validatedData.put("error", childData);
                return validatedData;
            }
            childData.put("DATA", excelData);
            validatedData.put("success", childData);
            System.out.println("excelData "+excelData.size());
            return validatedData;

        }catch(Exception e){
            e.printStackTrace();
            childData.put(e.getLocalizedMessage(), null);
            validatedData.put("error", childData);
            return validatedData;
        }
    }

    List<String> possibleDateFormats = new ArrayList<>(Arrays.asList(
            "dd/MM/yyyy",
            "MM-dd-yyyy",
            "yyyy.MM.dd",
            "dd/MMM/yyyy",
            "MM/dd/yy",
            "MM/dd/yyyy",
            "MM-dd-yy",
            "dd/MM/yy",
            "dd/MMM/yy",
            "dd-MM-yyyy",
            "dd-MM-yy"
    ));

    public String parseAndFormatDate(String inputDate) {
        for (String format : possibleDateFormats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.ENGLISH);
                sdf.setLenient(false); // strict parsing
                Date date = sdf.parse(inputDate);

                // If parsed successfully, format to desired output format
                SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MMM/yyyy", Locale.ENGLISH);
                return outputFormat.format(date);
            } catch (ParseException e) {
                // Try next format
            }
        }
        return null; // or throw exception / return error message
    }

    public Map<String, Map<String, List<String[]>>> checkAndValidateExamResultData(MultipartFile excelFile){
        String msg = "";
        Map<String, Map<String, List<String[]>>> validatedData = new HashMap<>();
        Map<String, List<String[]>> childData = new HashMap<>();
        try{
            boolean isValidFile = excelFileHandler.checkValidExcelFormat(excelFile);
            if(!isValidFile){
                childData.put("File format not supported or not valid", null);
                validatedData.put("error", childData);
                return validatedData;
            }
            List<String[]> excelData = excelFileHandler.excelExamResultDataToList(excelFile.getInputStream(), 2);
            if(excelData==null || excelData.isEmpty()){
                childData.put("Data not found or not valid", null);
                validatedData.put("error", childData);
                return validatedData;
            }

            //Read Data
            excelData = readSRExcelDataAndValidate(excelData, "exam_file");
            if(excelData==null || excelData.isEmpty()){
                childData.put("Unable to read data", null);
                validatedData.put("error", childData);
                return validatedData;
            }
            childData.put("DATA", excelData);
            validatedData.put("success", childData);
            System.out.println("excelData "+excelData.size());
            return validatedData;

        }catch(Exception e){
            e.printStackTrace();
            childData.put(e.getLocalizedMessage(), null);
            validatedData.put("error", childData);
            return validatedData;
        }
    }

}
