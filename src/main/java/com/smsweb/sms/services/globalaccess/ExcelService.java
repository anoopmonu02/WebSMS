package com.smsweb.sms.services.globalaccess;

import com.smsweb.sms.helper.ExcelFileHandler;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.ExamDetails;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.ExamResultSummary;
import com.smsweb.sms.models.universal.Grade;
import com.smsweb.sms.models.universal.Medium;
import com.smsweb.sms.models.universal.Section;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.repositories.student.ExamResultSummaryRepository;
import com.smsweb.sms.services.admin.ExaminationService;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
public class ExcelService {
    private static final Logger log = LoggerFactory.getLogger(ExcelService.class);

    private final GradeService gradeService;
    private final MediumService mediumService;
    private final SectionService sectionService;
    private final AcademicStudentService academicStudentService;
    private final ExcelFileHandler excelFileHandler;
    private final AcademicStudentRepository academicStudentRepository; // new — resolves ID# (uuid) during exam-result preview
    private final ExaminationService examinationService; // new — resolves exam name during exam-result preview
    private final ExamResultSummaryRepository examResultSummaryRepository; // new — duplicate-result detection during exam-result preview

    public ExcelService(GradeService gradeService, MediumService mediumService, SectionService sectionService, AcademicStudentService academicStudentService, ExcelFileHandler excelFileHandler, AcademicStudentRepository academicStudentRepository, ExaminationService examinationService, ExamResultSummaryRepository examResultSummaryRepository) {
        this.gradeService = gradeService;
        this.mediumService = mediumService;
        this.sectionService = sectionService;
        this.academicStudentService = academicStudentService;
        this.excelFileHandler = excelFileHandler;
        this.academicStudentRepository = academicStudentRepository;
        this.examinationService = examinationService;
        this.examResultSummaryRepository = examResultSummaryRepository;
    }


    public Map<String, Object> downloadSampleSRExcel(Long grade, Long section, Long medium, Long academic, Long school, String fileType, String calledFrom) {
        log.info("Inside downloadSampleSRExcel");
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

    /**
     * Admin/SuperAdmin-only — generates the bulk-correct download for a
     * grade/section, pre-filled with each student's CURRENT saved result for
     * the selected exam (blank where no result exists yet, same as the
     * regular sample file). See ExcelFileHandler.LoadCurrentMarksForCorrectionFile
     * for the exact fill rules.
     */
    public Map<String, Object> downloadCurrentExamResultExcel(Long grade, Long section, Long medium, Long academic, Long school, Long examId) {
        log.info("Inside downloadCurrentExamResultExcel");
        Map<String, Object> responseMap = new HashMap<>();
        try {
            Grade gradeObj = gradeService.getGradeById(grade).orElse(null);
            Section secObj = sectionService.getSectionById(section).orElse(null);
            Medium mediumObj = mediumService.getMediumById(medium).orElse(null);
            if (gradeObj == null) { responseMap.put("error", "Grade not found"); return responseMap; }
            if (secObj == null) { responseMap.put("error", "Section not found"); return responseMap; }
            if (mediumObj == null) { responseMap.put("error", "Medium not found"); return responseMap; }

            ExamDetails examDetails = examinationService.getExamDetailByDetailsId(examId);
            if (examDetails == null) { responseMap.put("error", "Examination not found"); return responseMap; }

            List<AcademicStudent> academicStudentList = academicStudentService.getAllAcademicStudentByGrade(medium, grade, section, academic, school);
            if (academicStudentList == null || academicStudentList.isEmpty()) {
                responseMap.put("error", "No student found");
                return responseMap;
            }

            // At most one existing result per student is carried into the
            // download — if a student has more than one (a genuine resit on
            // a different date), the latest by exam result date wins.
            Map<Long, ExamResultSummary> existingResultsByStudentId = new HashMap<>();
            for (ExamResultSummary existing : examResultSummaryRepository.findByExamDetailsId(examDetails.getId())) {
                if (existing.getAcademicStudent() == null) continue;
                Long studentId = existing.getAcademicStudent().getId();
                ExamResultSummary current = existingResultsByStudentId.get(studentId);
                if (current == null || (existing.getExamResultDate() != null && (current.getExamResultDate() == null || existing.getExamResultDate().after(current.getExamResultDate())))) {
                    existingResultsByStudentId.put(studentId, existing);
                }
            }

            String[] mediumGradeSection = { mediumObj.getMediumName(), gradeObj.getGradeName(), secObj.getSectionName() };
            ByteArrayInputStream excelFile = excelFileHandler.LoadCurrentMarksForCorrectionFile(
                    academicStudentList, mediumGradeSection, examDetails.getExamination().getExaminationName(), existingResultsByStudentId);
            responseMap.put("filecreated", excelFile);
            return responseMap;
        } catch (Exception e) {
            e.printStackTrace();
            responseMap.put("error", e.getLocalizedMessage());
            return responseMap;
        }
    }

    public Map<String, Map<String, List<String[]>>> checkAndValidateSRData(MultipartFile excelFile){
        log.info("Inside checkAndValidateSRData");
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
            log.debug("excelData size={}", excelData.size());
            return validatedData;

        }catch(Exception e){
            e.printStackTrace();
            childData.put(e.getLocalizedMessage(), null);
            validatedData.put("error", childData);
            return validatedData;
        }
    }

    public List<String[]> readSRExcelDataAndValidate(List<String[]> excelData, String fileName){
        log.info("Inside readSRExcelDataAndValidate");
        List<String[]> validatedData = new ArrayList<>();
        try{
            for(String[] rowData : excelData){
                if("exam_file".equalsIgnoreCase(fileName)){
                    // Columns: 0=Student Name,1=ID#,2=PSRN,3=Father Name,
                    // 4=Mother Name,5=Mobile,6=SR No,7=Exam Name,8=Exam Result
                    // Date,9=Total Marks,10=Obtained Marks,11=Percentage(%),
                    // 12=Division,13=Result,14=Remark,15=status flag (below).
                    // rowData[7-13] are the mandatory fields.
                    if (rowData.length < 16) {
                        rowData = Arrays.copyOf(rowData, 16);
                    }
                    boolean hasMissing = false;
                    for (int i = 7; i <= 13; i++) {
                        if (i >= rowData.length || rowData[i] == null || rowData[i].trim().isEmpty()) {
                            hasMissing = true;
                            break;
                        }
                    }
                    if(rowData[8]!=null && rowData[8].trim()!=""){
                        rowData[8] = parseAndFormatDate(rowData[8]);
                    }
                    if (hasMissing) {
                        rowData[15] = "error#####Failed: Mandatory fields for exam result are missing.";
                    } else {
                        rowData[15] = "success#####Passed";
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
        log.info("Inside downloadSampleAadharExcel");
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
        log.info("Inside checkAndValidateAadharData");
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
            log.debug("excelData size={}", excelData.size());
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
            "dd-MM-yy",
            "dd-MMM-yy",
            "dd-MMM-yyyy"
    ));

    public String parseAndFormatDate(String inputDate) {
        log.info("Inside parseAndFormatDate");
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

    /**
     * Validates an uploaded exam-result Excel file and returns a flat,
     * per-row structured result for the review-table UI (see stu_exam.html).
     *
     * Reuses readSRExcelDataAndValidate() UNCHANGED for the required-field
     * check (that method is shared with the SR and Aadhar upload flows —
     * do not touch it here). Everything below that point is new: resolving
     * the student by UUID, resolving the exam, flagging rows whose obtained
     * marks exceed total marks, and — new — flagging rows where a result
     * already exists for that student+exam (DUPLICATE), so the reviewer can
     * see it before saving instead of silently getting a second row.
     *
     * Return shape: {"error": "..."} on a file-level failure, or
     * {"totalRows": N, "readyCount": N, "issueCount": N, "rows": [ {status,
     * reason, ...all display fields...}, ... ]} on success. status is one
     * of READY / ISSUE / DUPLICATE.
     */
    public Map<String, Object> checkAndValidateExamResultData(MultipartFile excelFile, AcademicYear academicYear, School school){
        log.info("Inside checkAndValidateExamResultData");
        Map<String, Object> result = new HashMap<>();
        try{
            boolean isValidFile = excelFileHandler.checkValidExcelFormat(excelFile);
            if(!isValidFile){
                result.put("error", "File format not supported or not valid");
                return result;
            }
            List<String[]> excelData = excelFileHandler.excelExamResultDataToList(excelFile.getInputStream(), 2);
            if(excelData==null || excelData.isEmpty()){
                result.put("error", "Data not found or not valid");
                return result;
            }

            // Required-field + date-format check — unchanged, shared with SR/Aadhar uploads
            excelData = readSRExcelDataAndValidate(excelData, "exam_file");
            if(excelData==null || excelData.isEmpty()){
                result.put("error", "Unable to read data");
                return result;
            }

            // All rows in one upload share the same exam (enforced again at
            // save time in StudentService.uploadExamResult) — resolve it once.
            // Column 7 = Exam Name (see readSRExcelDataAndValidate's
            // exam_file column map — PSRN at index 2 shifted everything
            // after ID# by +1).
            String examName = null;
            for(String[] row : excelData){
                if(row.length > 7 && row[7] != null && !row[7].trim().isEmpty()){
                    examName = row[7].trim();
                    break;
                }
            }
            ExamDetails examDetails = null;
            if(examName != null && academicYear != null && school != null){
                examDetails = examinationService.getExamDetailByName(examName, academicYear.getId(), school.getId());
            }

            // Keyed by "studentId|dd/MMM/yyyy" so a different result date for the
            // same student+exam (a genuine resit) is NOT treated as a duplicate —
            // only the exact same student+exam+date is.
            Set<String> existingStudentDateKeys = new HashSet<>();
            if(examDetails != null){
                SimpleDateFormat keyFormat = new SimpleDateFormat("dd/MMM/yyyy", Locale.ENGLISH);
                for(Object[] pair : examResultSummaryRepository.findStudentIdAndResultDateByExamDetailsId(examDetails.getId())){
                    Long studentId = (Long) pair[0];
                    Date date = (Date) pair[1];
                    existingStudentDateKeys.add(studentId + "|" + (date != null ? keyFormat.format(date) : ""));
                }
            }

            String[] requiredLabels = {"Exam Name","Exam Result Date","Total Marks","Obtained Marks","Percentage(%)","Division","Result"};

            List<Map<String, Object>> rows = new ArrayList<>();
            int readyCount = 0, issueCount = 0, rowIndex = 0;
            for(String[] row : excelData){
                rowIndex++;
                Map<String, Object> rowMap = new LinkedHashMap<>();
                rowMap.put("rowIndex", rowIndex);
                rowMap.put("studentName", cell(row, 0));
                rowMap.put("idNo", cell(row, 1));
                rowMap.put("psrn", cell(row, 2));
                rowMap.put("fatherName", cell(row, 3));
                rowMap.put("motherName", cell(row, 4));
                rowMap.put("mobile", cell(row, 5));
                rowMap.put("sr", cell(row, 6));
                rowMap.put("examName", cell(row, 7));
                rowMap.put("examResultDate", cell(row, 8));
                rowMap.put("totalMarks", cell(row, 9));
                rowMap.put("obtainedMarks", cell(row, 10));
                rowMap.put("percentage", cell(row, 11));
                rowMap.put("division", cell(row, 12));
                rowMap.put("result", cell(row, 13));
                rowMap.put("remark", cell(row, 14));

                String status = "READY";
                String reason = "";

                List<String> missing = new ArrayList<>();
                for(int i = 7; i <= 13; i++){
                    String v = cell(row, i);
                    if(v == null || v.trim().isEmpty()){
                        missing.add(requiredLabels[i-7]);
                    }
                }
                if(!missing.isEmpty()){
                    status = "ISSUE";
                    reason = String.join(", ", missing) + (missing.size() > 1 ? " are missing" : " is missing");
                } else {
                    AcademicStudent academicStudent = null;
                    String uuid = cell(row, 1);
                    try{
                        if(uuid != null && !uuid.trim().isEmpty()){
                            academicStudent = academicStudentRepository.findByUuid(UUID.fromString(uuid.trim())).orElse(null);
                        }
                    }catch(IllegalArgumentException iae){
                        academicStudent = null;
                    }
                    if(academicStudent == null){
                        status = "ISSUE";
                        reason = "No student found for this ID in the current school/academic year";
                    } else if(academicYear != null && academicStudent.getAcademicYear() != null
                            && !academicStudent.getAcademicYear().getId().equals(academicYear.getId())){
                        // Safety net for the Session (current/previous year) selector on the exam-
                        // result screen — findByUuid() is deliberately global (no school/year filter,
                        // see its call site), so it's possible for a file built for one session to be
                        // uploaded while a different session is selected. Catch that mismatch here
                        // instead of silently saving a result whose academicYear disagrees with its
                        // own academicStudent's academicYear.
                        status = "ISSUE";
                        reason = "This student belongs to a different academic year than the one selected above";
                    } else if(examDetails == null){
                        status = "ISSUE";
                        reason = "Examination '" + examName + "' not found for this school/academic year";
                    } else {
                        // Long, not Double — ExamResultSummary.totalMarks/obtainedMarks are
                        // Long-typed, and the save step (StudentService.uploadExamResult) does
                        // Long.parseLong on these same two fields, so validation must accept
                        // exactly what save will accept, or a row could show "Ready" here and
                        // then fail at save time.
                        Long total = parseLongOrNull(cell(row, 9));
                        Long obtained = parseLongOrNull(cell(row, 10));
                        if(total == null || obtained == null){
                            status = "ISSUE";
                            reason = "Total/obtained marks must be whole numbers";
                        } else if(obtained > total){
                            status = "ISSUE";
                            reason = "Obtained marks exceed total marks";
                        } else {
                            // row[8] is already normalized to dd/MMM/yyyy by
                            // readSRExcelDataAndValidate() above, matching the
                            // key format built from the DB dates.
                            String rowDateKey = academicStudent.getId() + "|" + cell(row, 8);
                            if(existingStudentDateKeys.contains(rowDateKey)){
                                status = "DUPLICATE";
                                reason = "Result already saved for this student on this exam and date";
                            }
                        }
                    }
                }

                rowMap.put("status", status);
                rowMap.put("reason", reason);
                rows.add(rowMap);

                if("READY".equals(status)){
                    readyCount++;
                } else {
                    issueCount++;
                }
            }

            result.put("totalRows", rows.size());
            result.put("readyCount", readyCount);
            result.put("issueCount", issueCount);
            result.put("rows", rows);
            return result;

        }catch(Exception e){
            e.printStackTrace();
            result.put("error", e.getLocalizedMessage());
            return result;
        }
    }

    /**
     * Admin/SuperAdmin-only bulk-correct preview. Same file format and
     * required-field/marks validation as checkAndValidateExamResultData, but
     * classifies each valid row as NEW (no existing result for this
     * student+exam+date — will be inserted), UPDATE (an existing result
     * exists and at least one value differs — will be overwritten, with the
     * old values captured here for the review table), or UNCHANGED (existing
     * result found, values identical — will be skipped, no write, no audit
     * entry). ISSUE rows are blocked exactly as in the regular upload.
     *
     * Unlike the regular upload, this does NOT treat an existing result as a
     * blocking duplicate — the whole point of this flow is to allow
     * overwriting it, gated to Admin/SuperAdmin only at the controller layer.
     */
    public Map<String, Object> checkAndClassifyBulkCorrectionData(MultipartFile excelFile, AcademicYear academicYear, School school){
        log.info("Inside checkAndClassifyBulkCorrectionData");
        Map<String, Object> result = new HashMap<>();
        try{
            boolean isValidFile = excelFileHandler.checkValidExcelFormat(excelFile);
            if(!isValidFile){
                result.put("error", "File format not supported or not valid");
                return result;
            }
            List<String[]> excelData = excelFileHandler.excelExamResultDataToList(excelFile.getInputStream(), 2);
            if(excelData==null || excelData.isEmpty()){
                result.put("error", "Data not found or not valid");
                return result;
            }

            excelData = readSRExcelDataAndValidate(excelData, "exam_file");
            if(excelData==null || excelData.isEmpty()){
                result.put("error", "Unable to read data");
                return result;
            }

            // Column 7 = Exam Name (PSRN at index 2 shifted everything after
            // ID# by +1 — see readSRExcelDataAndValidate's exam_file map).
            String examName = null;
            for(String[] row : excelData){
                if(row.length > 7 && row[7] != null && !row[7].trim().isEmpty()){
                    examName = row[7].trim();
                    break;
                }
            }
            ExamDetails examDetails = null;
            if(examName != null && academicYear != null && school != null){
                examDetails = examinationService.getExamDetailByName(examName, academicYear.getId(), school.getId());
            }

            // Keyed by "studentId|dd/MMM/yyyy" — same key shape used for
            // duplicate detection in the regular upload, but mapped to the
            // full entity here since we need old values, not just a flag.
            Map<String, ExamResultSummary> existingByStudentDateKey = new HashMap<>();
            if(examDetails != null){
                SimpleDateFormat keyFormat = new SimpleDateFormat("dd/MMM/yyyy", Locale.ENGLISH);
                for(ExamResultSummary existing : examResultSummaryRepository.findByExamDetailsId(examDetails.getId())){
                    if(existing.getAcademicStudent() == null) continue;
                    String key = existing.getAcademicStudent().getId() + "|" + (existing.getExamResultDate() != null ? keyFormat.format(existing.getExamResultDate()) : "");
                    existingByStudentDateKey.put(key, existing);
                }
            }

            String[] requiredLabels = {"Exam Name","Exam Result Date","Total Marks","Obtained Marks","Percentage(%)","Division","Result"};

            List<Map<String, Object>> rows = new ArrayList<>();
            int newCount = 0, updateCount = 0, unchangedCount = 0, issueCount = 0, rowIndex = 0;
            for(String[] row : excelData){
                rowIndex++;
                Map<String, Object> rowMap = new LinkedHashMap<>();
                rowMap.put("rowIndex", rowIndex);
                rowMap.put("studentName", cell(row, 0));
                rowMap.put("idNo", cell(row, 1));
                rowMap.put("psrn", cell(row, 2));
                rowMap.put("fatherName", cell(row, 3));
                rowMap.put("motherName", cell(row, 4));
                rowMap.put("mobile", cell(row, 5));
                rowMap.put("sr", cell(row, 6));
                rowMap.put("examName", cell(row, 7));
                rowMap.put("examResultDate", cell(row, 8));
                rowMap.put("totalMarks", cell(row, 9));
                rowMap.put("obtainedMarks", cell(row, 10));
                rowMap.put("percentage", cell(row, 11));
                rowMap.put("division", cell(row, 12));
                rowMap.put("result", cell(row, 13));
                rowMap.put("remark", cell(row, 14));
                rowMap.put("oldTotalMarks", null);
                rowMap.put("oldObtainedMarks", null);
                rowMap.put("oldPercentage", null);
                rowMap.put("oldDivision", null);
                rowMap.put("oldResult", null);
                rowMap.put("oldRemark", null);

                String status;
                String reason = "";

                List<String> missing = new ArrayList<>();
                for(int i = 7; i <= 13; i++){
                    String v = cell(row, i);
                    if(v == null || v.trim().isEmpty()){
                        missing.add(requiredLabels[i-7]);
                    }
                }
                if(!missing.isEmpty()){
                    status = "ISSUE";
                    reason = String.join(", ", missing) + (missing.size() > 1 ? " are missing" : " is missing");
                } else {
                    AcademicStudent academicStudent = null;
                    String uuid = cell(row, 1);
                    try{
                        if(uuid != null && !uuid.trim().isEmpty()){
                            academicStudent = academicStudentRepository.findByUuid(UUID.fromString(uuid.trim())).orElse(null);
                        }
                    }catch(IllegalArgumentException iae){
                        academicStudent = null;
                    }
                    if(academicStudent == null){
                        status = "ISSUE";
                        reason = "No student found for this ID in the current school/academic year";
                    } else if(academicYear != null && academicStudent.getAcademicYear() != null
                            && !academicStudent.getAcademicYear().getId().equals(academicYear.getId())){
                        // Same safety net as checkAndValidateExamResultData — see comment there.
                        status = "ISSUE";
                        reason = "This student belongs to a different academic year than the one selected above";
                    } else if(examDetails == null){
                        status = "ISSUE";
                        reason = "Examination '" + examName + "' not found for this school/academic year";
                    } else {
                        Long total = parseLongOrNull(cell(row, 9));
                        Long obtained = parseLongOrNull(cell(row, 10));
                        if(total == null || obtained == null){
                            status = "ISSUE";
                            reason = "Total/obtained marks must be whole numbers";
                        } else if(obtained > total){
                            status = "ISSUE";
                            reason = "Obtained marks exceed total marks";
                        } else {
                            String rowDateKey = academicStudent.getId() + "|" + cell(row, 8);
                            ExamResultSummary existing = existingByStudentDateKey.get(rowDateKey);
                            if(existing == null){
                                status = "NEW";
                                reason = "No existing result — will be inserted";
                            } else {
                                rowMap.put("oldTotalMarks", existing.getTotalMarks());
                                rowMap.put("oldObtainedMarks", existing.getObtainedMarks());
                                rowMap.put("oldPercentage", existing.getPercentageMarks());
                                rowMap.put("oldDivision", existing.getDivision());
                                rowMap.put("oldResult", existing.getResult());
                                rowMap.put("oldRemark", existing.getRemarks());

                                boolean changed = !Objects.equals(existing.getTotalMarks(), total)
                                        || !Objects.equals(existing.getObtainedMarks(), obtained)
                                        || !percentageMatches(existing.getPercentageMarks(), cell(row, 11))
                                        || !valueMatches(existing.getDivision(), cell(row, 12))
                                        || !valueMatches(existing.getResult(), cell(row, 13))
                                        || !valueMatches(existing.getRemarks(), cell(row, 14));
                                if(changed){
                                    status = "UPDATE";
                                    reason = "Existing result found — will be overwritten";
                                } else {
                                    status = "UNCHANGED";
                                    reason = "Matches the currently saved result — no change";
                                }
                            }
                        }
                    }
                }

                rowMap.put("status", status);
                rowMap.put("reason", reason);
                rows.add(rowMap);

                switch(status){
                    case "NEW": newCount++; break;
                    case "UPDATE": updateCount++; break;
                    case "UNCHANGED": unchangedCount++; break;
                    default: issueCount++;
                }
            }

            result.put("totalRows", rows.size());
            result.put("newCount", newCount);
            result.put("updateCount", updateCount);
            result.put("unchangedCount", unchangedCount);
            result.put("issueCount", issueCount);
            result.put("rows", rows);
            return result;

        }catch(Exception e){
            e.printStackTrace();
            result.put("error", e.getLocalizedMessage());
            return result;
        }
    }

    private boolean valueMatches(String existingValue, String newValue){
        String a = existingValue == null ? "" : existingValue.trim();
        String b = newValue == null ? "" : newValue.trim();
        return a.equals(b);
    }

    private boolean percentageMatches(Double existingValue, String newValue){
        Double a = existingValue;
        Double b;
        try{
            b = (newValue == null || newValue.trim().isEmpty()) ? null : Double.parseDouble(newValue.trim());
        }catch(NumberFormatException e){
            b = null;
        }
        if(a == null && b == null) return true;
        if(a == null || b == null) return false;
        return Math.abs(a - b) < 0.0001;
    }

    private String cell(String[] row, int index){
        if(row == null || index >= row.length) return null;
        return row[index];
    }

    private Long parseLongOrNull(String s){
        if(s == null || s.trim().isEmpty()) return null;
        try{
            return Long.parseLong(s.trim());
        }catch(NumberFormatException e){
            return null;
        }
    }

}
