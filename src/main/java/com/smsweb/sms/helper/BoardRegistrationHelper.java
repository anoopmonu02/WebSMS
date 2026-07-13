package com.smsweb.sms.helper;

import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.student.StudentRegionalDetail;
import com.smsweb.sms.models.universal.Category;
import com.smsweb.sms.models.universal.Medium;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Builds "Board Registration (Class 9)" rows in the exact government-mandated column order,
 * and the matching single-sheet .xlsx export. One row-building method feeds both the on-screen
 * preview (JSON) and the downloaded file, so the two can never show different data.
 *
 * Column order is fixed by the government template — DO NOT reorder HEADER.
 *
 * Mapping decisions baked in here (confirmed with the school before building):
 *   - Sex: MALE->1, FEMALE->2, anything else (incl. NO_PREFERENCE)->0.
 *   - CasteCode: Category name matched case-insensitively to SC/ST/OBC/GEN(ERAL)/EWS -> 1-5.
 *     Unmapped category text leaves the cell blank and flags the row (does not block export).
 *   - IsMinorityCode: 1 only when the student's Category row has DB id 4, else 0 — this is a
 *     literal "category id == 4" check, not a text match against the SC/ST/OBC/Gen/EWS legend.
 *   - MediumCode: Medium name containing "HIN"->1, containing "ENG"->2, else blank + flagged.
 *   - District: no District concept exists in this schema — Student.city is used as the closest
 *     available field. Flagged when city is not set.
 *   - SerialNumber: plain 1-based row number (zero-padded to 4 digits), ordered by student name.
 *   - Subject/UniqueIDClass08/SrNumber/Address2-4/NationalityOther: always blank — filled in by
 *     the school after export, per their process.
 */
@Component
public class BoardRegistrationHelper {

    public static final String[] HEADER = {
            "SerialNumber", "CandidateName", "FatherName", "MotherName", "CandidateName_HIN",
            "FatherName_HIN", "MotherName_HIN", "DD", "MM", "YYYY", "Sex", "CasteCode", "IsMinorityCode",
            "CandidateType1Code", "CandidateType2Code", "MediumCode", "Subject01Code", "Subject02Code",
            "Subject03Code", "Subject04Code", "Subject05Code", "Subject06Code", "Subject07Code",
            "SubjectVOCCode", "SubjectRevVOCCode", "MobileNumber", "AadhaarNumber", "EMAILID",
            "Address1", "Address2", "Address3", "Address4", "District", "PinCode", "State",
            "UniqueIDClass08", "Nationality", "NationalityOther", "ApaarId", "PenNumber", "SrNumber"
    };

    private static final String[] BLANK_SUBJECT_COLUMNS = {
            "Subject01Code", "Subject02Code", "Subject03Code", "Subject04Code",
            "Subject05Code", "Subject06Code", "Subject07Code", "SubjectVOCCode", "SubjectRevVOCCode"
    };

    private static final Map<String, String> CASTE_CODE_MAP = new HashMap<>();
    static {
        CASTE_CODE_MAP.put("SC", "1");
        CASTE_CODE_MAP.put("ST", "2");
        CASTE_CODE_MAP.put("OBC", "3");
        CASTE_CODE_MAP.put("GEN", "4");
        CASTE_CODE_MAP.put("GENERAL", "4");
        CASTE_CODE_MAP.put("EWS", "5");
    }

    /**
     * @param students             academic students already filtered to Medium/Grade 9/Section (Active only)
     * @param regionalByStudentId  StudentRegionalDetail keyed by Student.id, for the Hindi-name columns
     */
    public List<Map<String, Object>> buildRows(List<AcademicStudent> students,
                                                Map<Long, StudentRegionalDetail> regionalByStudentId) {
        List<AcademicStudent> sorted = new ArrayList<>(students);
        sorted.sort(Comparator.comparing(as ->
                (as.getStudent() != null && as.getStudent().getStudentName() != null)
                        ? as.getStudent().getStudentName() : ""));

        List<Map<String, Object>> rows = new ArrayList<>();
        int rowNum = 1;
        for (AcademicStudent as : sorted) {
            rows.add(buildRow(as, rowNum++, regionalByStudentId));
        }
        return rows;
    }

    private Map<String, Object> buildRow(AcademicStudent as, int rowNum, Map<Long, StudentRegionalDetail> regionalByStudentId) {
        Student s = as.getStudent();
        StudentRegionalDetail regional = (s != null) ? regionalByStudentId.get(s.getId()) : null;

        List<String> flags = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();

        row.put("SerialNumber", String.valueOf(rowNum));
        row.put("CandidateName", nullSafe(s.getStudentName()));
        row.put("FatherName", nullSafe(s.getFatherName()));
        row.put("MotherName", nullSafe(s.getMotherName()));
        row.put("CandidateName_HIN", regional != null ? nullSafe(regional.getStudentNameRegional()) : "");
        row.put("FatherName_HIN", regional != null ? nullSafe(regional.getFatherNameRegional()) : "");
        row.put("MotherName_HIN", regional != null ? nullSafe(regional.getMotherNameRegional()) : "");

        if (regional == null) {
            flags.add("No Hindi (regional) name saved for this student");
        }

        if (s.getDob() != null) {
            row.put("DD", new SimpleDateFormat("dd").format(s.getDob()));
            row.put("MM", new SimpleDateFormat("MM").format(s.getDob()));
            row.put("YYYY", new SimpleDateFormat("yyyy").format(s.getDob()));
        } else {
            row.put("DD", "");
            row.put("MM", "");
            row.put("YYYY", "");
            flags.add("Date of birth missing");
        }

        row.put("Sex", mapGender(s.getGender()));

        row.put("CasteCode", mapCasteCode(s.getCategory(), flags));
        row.put("IsMinorityCode", (s.getCategory() != null && Long.valueOf(4L).equals(s.getCategory().getId())) ? "1" : "0");

        row.put("CandidateType1Code", "1");
        row.put("CandidateType2Code", "0");

        row.put("MediumCode", mapMediumCode(as.getMedium(), flags));

        for (String subjectCol : BLANK_SUBJECT_COLUMNS) {
            row.put(subjectCol, "");
        }

        row.put("MobileNumber", nullSafe(s.getMobile1()));
        row.put("AadhaarNumber", nullSafe(s.getAadharNo()));
        row.put("EMAILID", (s.getUserEntity() != null) ? nullSafe(s.getUserEntity().getEmail()) : "");

        row.put("Address1", nullSafe(s.getAddress()));
        row.put("Address2", "");
        row.put("Address3", "");
        row.put("Address4", "");

        if (s.getCity() != null) {
            row.put("District", nullSafe(s.getCity().getCityName()));
        } else {
            row.put("District", "");
            flags.add("City not set (used as District — no District field exists in the system)");
        }

        row.put("PinCode", nullSafe(s.getPincode()));
        row.put("State", (s.getProvince() != null) ? nullSafe(s.getProvince().getProvinceName()) : "");

        row.put("UniqueIDClass08", "");
        row.put("Nationality", nullSafe(s.getNationality()));
        row.put("NationalityOther", "");
        row.put("ApaarId", nullSafe(s.getApaarId()));
        row.put("PenNumber", nullSafe(s.getPenNo()));
        row.put("SrNumber", "");

        row.put("_flagged", !flags.isEmpty());
        row.put("_flagReasons", String.join("; ", flags));
        row.put("_studentId", s.getId());

        return row;
    }

    private String mapGender(String gender) {
        if (gender == null) return "0";
        if (gender.equalsIgnoreCase("MALE")) return "1";
        if (gender.equalsIgnoreCase("FEMALE")) return "2";
        return "0";
    }

    private String mapCasteCode(Category category, List<String> flags) {
        if (category == null || category.getCategoryName() == null) {
            flags.add("Category not set");
            return "";
        }
        String key = category.getCategoryName().trim().toUpperCase();
        String code = CASTE_CODE_MAP.get(key);
        if (code == null) {
            flags.add("Category \"" + category.getCategoryName() + "\" doesn't map to SC/ST/OBC/Gen/EWS");
            return "";
        }
        return code;
    }

    private String mapMediumCode(Medium medium, List<String> flags) {
        if (medium == null || medium.getMediumName() == null) {
            flags.add("Medium not set");
            return "";
        }
        String name = medium.getMediumName().trim().toUpperCase();
        if (name.contains("HIN")) return "1";
        if (name.contains("ENG")) return "2";
        flags.add("Medium \"" + medium.getMediumName() + "\" doesn't map to Hindi/English");
        return "";
    }

    private String nullSafe(String v) {
        return v == null ? "" : v.trim();
    }

    /** Writes the rows into a single sheet named "Candidate09", columns in the fixed HEADER order. */
    public byte[] buildExcel(List<Map<String, Object>> rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Candidate09");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADER.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADER[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (Map<String, Object> rowData : rows) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 0; i < HEADER.length; i++) {
                    Object value = rowData.get(HEADER[i]);
                    row.createCell(i).setCellValue(value != null ? value.toString() : "");
                }
            }

            for (int i = 0; i < HEADER.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
