package com.smsweb.sms.services.student;

import com.smsweb.sms.dto.PsrnUpdatePreviewResult;
import com.smsweb.sms.dto.PsrnUpdateRow;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.repositories.student.StudentRepository;
import com.smsweb.sms.services.users.UserService;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;

/**
 * PSRN-based bulk update of Student.penNo / Student.apaarId from an uploaded sheet.
 *
 * Match key is PSRN alone (globally unique on Student, see StudentRepository.findByPsrn).
 * Student Name / Father Name / Mother Name / Grade / Sec are used only as a secondary
 * data-quality check — a mismatch produces a WARNING row, never blocks the match itself.
 *
 * This is a brand-new, fully separate flow — does not touch StudentImportService or any
 * existing student-creation code path.
 */
@Service
public class PsrnBulkUpdateService {

    private static final Logger log = LoggerFactory.getLogger(PsrnBulkUpdateService.class);

    public static final String FIELD_PEN_NO   = "PEN_NO";
    public static final String FIELD_APAAR_ID = "APAAR_ID";

    // Header text (case-insensitive, trimmed) expected in the sheet.
    private static final String COL_PSRN    = "PSRN#";
    private static final String COL_PEN_NO  = "PEN No";
    private static final String COL_APAAR   = "Apaar ID";
    private static final String COL_NAME    = "Student Name";
    private static final String COL_FATHER  = "Father Name";
    private static final String COL_MOTHER  = "Mother Name";
    private static final String COL_GRADE   = "Grade";
    private static final String COL_SECTION = "Sec";

    private static final int MAX_HEADER_SCAN_ROWS = 10;

    private final StudentRepository studentRepository;
    private final AcademicStudentRepository academicStudentRepository;
    private final UserService userService;

    public PsrnBulkUpdateService(StudentRepository studentRepository,
                                  AcademicStudentRepository academicStudentRepository,
                                  UserService userService) {
        this.studentRepository = studentRepository;
        this.academicStudentRepository = academicStudentRepository;
        this.userService = userService;
    }

    /** Step 2 — parse + validate, no DB writes. */
    public PsrnUpdatePreviewResult parseAndValidate(byte[] fileBytes, String targetField) {
        log.info("Inside parseAndValidate - targetField={}", targetField);
        PsrnUpdatePreviewResult result = new PsrnUpdatePreviewResult();
        result.setTargetField(targetField);
        result.setRows(parseRows(fileBytes, targetField, result.getMissingOptionalColumns()));
        result.recalcCounts();
        return result;
    }

    /** Step 3 — re-parses the file fresh (never trusts the session-stored preview object for
     *  the actual DB mutation, same defensive pattern as StudentImportService.executeImport)
     *  and saves every row that's READY, or WARNING when includeWarnings=true. */
    @Transactional
    public PsrnUpdatePreviewResult executeUpdate(byte[] fileBytes, String targetField, boolean includeWarnings) {
        log.info("Inside executeUpdate - targetField={}, includeWarnings={}", targetField, includeWarnings);
        PsrnUpdatePreviewResult result = new PsrnUpdatePreviewResult();
        result.setTargetField(targetField);
        List<PsrnUpdateRow> rows = parseRows(fileBytes, targetField, result.getMissingOptionalColumns());
        result.setRows(rows);

        UserEntity loggedInUser = userService.getLoggedInUser();

        for (PsrnUpdateRow row : rows) {
            boolean shouldSave = PsrnUpdateRow.STATUS_READY.equals(row.getStatus())
                    || (PsrnUpdateRow.STATUS_WARNING.equals(row.getStatus()) && includeWarnings);
            if (!shouldSave) continue;

            try {
                Student student = studentRepository.findById(row.getStudentId())
                        .orElseThrow(() -> new IllegalStateException("Student no longer exists"));
                if (FIELD_PEN_NO.equals(targetField)) {
                    student.setPenNo(row.getSheetValue());
                } else {
                    student.setApaarId(row.getSheetValue());
                }
                student.setUpdatedBy(loggedInUser);
                studentRepository.save(student);
                row.setStatus(PsrnUpdateRow.STATUS_UPDATED);
                row.setMessage("Updated successfully.");
            } catch (Exception e) {
                // Caught per-row so one bad row can't abort the whole batch or roll back
                // rows already saved earlier in the loop.
                log.error("Failed to save PSRN row {} (psrn={})", row.getRowNum(), row.getPsrn(), e);
                row.setStatus(PsrnUpdateRow.STATUS_ERROR);
                row.setMessage("Save failed: " + e.getMessage());
            }
        }

        result.recalcCounts();
        return result;
    }

    // ── Parsing ──────────────────────────────────────────────────────────────

    private List<PsrnUpdateRow> parseRows(byte[] fileBytes, String targetField, List<String> missingOptionalColumnsOut) {
        List<PsrnUpdateRow> rows = new ArrayList<>();
        boolean wantPenNo = FIELD_PEN_NO.equals(targetField);

        try (InputStream is = new ByteArrayInputStream(fileBytes);
             Workbook wb = WorkbookFactory.create(is)) {

            Sheet sheet = wb.getSheetAt(0);

            Map<String, Integer> headerIndex = findHeaderRow(sheet);
            if (headerIndex == null) {
                throw new IllegalArgumentException("Could not find a header row containing '" + COL_PSRN + "' in the first " + MAX_HEADER_SCAN_ROWS + " rows.");
            }
            int headerRowNum = headerIndex.remove(HEADER_ROW_MARKER);

            Integer psrnCol = headerIndex.get(norm(COL_PSRN));
            Integer targetCol = headerIndex.get(norm(wantPenNo ? COL_PEN_NO : COL_APAAR));
            String targetColName = wantPenNo ? COL_PEN_NO : COL_APAAR;

            if (psrnCol == null) {
                throw new IllegalArgumentException("Sheet is missing the required '" + COL_PSRN + "' column.");
            }
            if (targetCol == null) {
                throw new IllegalArgumentException("Sheet is missing the required '" + targetColName + "' column for this update type.");
            }

            Integer nameCol = headerIndex.get(norm(COL_NAME));
            Integer fatherCol = headerIndex.get(norm(COL_FATHER));
            Integer motherCol = headerIndex.get(norm(COL_MOTHER));
            Integer gradeCol = headerIndex.get(norm(COL_GRADE));
            Integer sectionCol = headerIndex.get(norm(COL_SECTION));

            if (nameCol == null) missingOptionalColumnsOut.add(COL_NAME);
            if (fatherCol == null) missingOptionalColumnsOut.add(COL_FATHER);
            if (motherCol == null) missingOptionalColumnsOut.add(COL_MOTHER);
            if (gradeCol == null) missingOptionalColumnsOut.add(COL_GRADE);
            if (sectionCol == null) missingOptionalColumnsOut.add(COL_SECTION);

            for (Row row : sheet) {
                if (row.getRowNum() <= headerRowNum) continue;

                String psrnRaw = cellToString(row, psrnCol);
                String sheetValueRaw = cellToString(row, targetCol);

                // Fully blank row (no PSRN at all) — not an error, just not present. Skip silently.
                if (psrnRaw.isBlank() && sheetValueRaw.isBlank()
                        && cellToString(row, nameCol).isBlank()) {
                    continue;
                }

                PsrnUpdateRow r = new PsrnUpdateRow();
                r.setRowNum(row.getRowNum() + 1);
                r.setPsrnRaw(psrnRaw);
                r.setSheetValue(sheetValueRaw.trim());
                r.setSheetStudentName(cellToString(row, nameCol));
                r.setSheetFatherName(cellToString(row, fatherCol));
                r.setSheetMotherName(cellToString(row, motherCol));
                r.setSheetGrade(cellToString(row, gradeCol));
                r.setSheetSection(cellToString(row, sectionCol));

                processRow(r, wantPenNo);
                rows.add(r);
            }

        } catch (IllegalArgumentException iae) {
            throw iae; // structural errors (missing columns) surface as-is to the controller
        } catch (Exception e) {
            log.error("Failed to parse PSRN update sheet", e);
            throw new RuntimeException("Failed to parse Excel file: " + e.getMessage(), e);
        }

        return rows;
    }

    private void processRow(PsrnUpdateRow r, boolean wantPenNo) {
        // 1. PSRN must be present and parseable.
        if (r.getPsrnRaw() == null || r.getPsrnRaw().isBlank()) {
            r.setStatus(PsrnUpdateRow.STATUS_ERROR);
            r.setMessage("PSRN is blank.");
            return;
        }
        Long psrn;
        try {
            // Strip a trailing ".0" some spreadsheet tools add to numeric-looking text cells.
            String cleaned = r.getPsrnRaw().trim().replaceAll("\\.0$", "");
            psrn = Long.parseLong(cleaned);
        } catch (NumberFormatException e) {
            r.setStatus(PsrnUpdateRow.STATUS_ERROR);
            r.setMessage("PSRN '" + r.getPsrnRaw() + "' is not a valid number.");
            return;
        }
        r.setPsrn(psrn);

        // 2. Target value: blank means "leave for blank data" — skip, not an error.
        if (r.getSheetValue() == null || r.getSheetValue().isBlank()) {
            r.setStatus(PsrnUpdateRow.STATUS_SKIP_BLANK);
            r.setMessage("Sheet value is blank — left unchanged.");
            return;
        }

        // 3. Format validation, same pattern as Student.penNo / Student.apaarId.
        String pattern = wantPenNo ? "[0-9]{11}" : "[0-9]{12}";
        String label = wantPenNo ? "PEN No" : "Apaar ID";
        if (!r.getSheetValue().matches(pattern)) {
            r.setStatus(PsrnUpdateRow.STATUS_ERROR);
            r.setMessage("Invalid " + label + " format (must be " + (wantPenNo ? "11" : "12") + " digits) — got '" + r.getSheetValue() + "'.");
            return;
        }

        // 4. Match the student.
        Student student = studentRepository.findByPsrn(psrn).orElse(null);
        if (student == null) {
            r.setStatus(PsrnUpdateRow.STATUS_ERROR);
            r.setMessage("No student found with PSRN " + psrn + ".");
            return;
        }
        r.setStudentId(student.getId());
        r.setDbStudentName(student.getStudentName());
        r.setDbFatherName(student.getFatherName());
        r.setDbMotherName(student.getMotherName());

        // Grade/Section MUST come from the student's current (isMigrated=false) AcademicStudent
        // enrollment row, never from Student.grade/Student.section directly — those two fields
        // are only an admission-time snapshot on the Student entity and are never touched by
        // AcademicStudentService.updateGradeSection or by promotion/migration (only the
        // AcademicStudent row is updated there). Using Student.grade/Section here produced
        // false-positive WARNINGs for every student re-sectioned or promoted since admission.
        String currentGrade = null;
        String currentSection = null;
        try {
            AcademicStudent current = academicStudentRepository
                    .findByStudent_IdAndStatusAndIsMigrated(student.getId(), "Active", false)
                    .orElse(null);
            if (current != null) {
                currentGrade = current.getGrade() != null ? current.getGrade().getGradeName() : null;
                currentSection = current.getSection() != null ? current.getSection().getSectionName() : null;
            }
        } catch (Exception e) {
            log.warn("Could not resolve current enrollment for student {} (psrn={}), falling back to Student.grade/section", student.getId(), psrn, e);
        }
        // Defensive fallback only — should not normally trigger for an active student.
        if (currentGrade == null) currentGrade = student.getGrade() != null ? student.getGrade().getGradeName() : null;
        if (currentSection == null) currentSection = student.getSection() != null ? student.getSection().getSectionName() : null;

        r.setDbGrade(currentGrade);
        r.setDbSection(currentSection);

        String currentValue = wantPenNo ? student.getPenNo() : student.getApaarId();
        r.setDbValue(currentValue);

        // 5. Conflict check — an existing, different value is never auto-overwritten.
        if (currentValue != null && !currentValue.isBlank()) {
            if (currentValue.trim().equalsIgnoreCase(r.getSheetValue())) {
                r.setStatus(PsrnUpdateRow.STATUS_SKIP_ALREADY_SET);
                r.setMessage("Already set to this value.");
            } else {
                r.setStatus(PsrnUpdateRow.STATUS_CONFLICT);
                r.setMessage("DB already has a different " + label + " (" + currentValue + ") — flagged for manual review, not updated.");
            }
            return;
        }

        // 6. Mismatch check (only for columns actually present in the sheet).
        List<String> mismatches = r.getMismatchFields();
        addIfMismatch(mismatches, "Student Name", r.getSheetStudentName(), r.getDbStudentName());
        addIfMismatch(mismatches, "Father Name", r.getSheetFatherName(), r.getDbFatherName());
        addIfMismatch(mismatches, "Mother Name", r.getSheetMotherName(), r.getDbMotherName());
        addIfMismatch(mismatches, "Grade", r.getSheetGrade(), r.getDbGrade());
        addIfMismatch(mismatches, "Section", r.getSheetSection(), r.getDbSection());

        if (!mismatches.isEmpty()) {
            r.setStatus(PsrnUpdateRow.STATUS_WARNING);
            r.setMessage("Mismatch on: " + String.join(", ", mismatches) + " — will update if confirmed.");
        } else {
            r.setStatus(PsrnUpdateRow.STATUS_READY);
            r.setMessage("Ready to update.");
        }
    }

    /** Adds `label` to mismatches when both sides are non-blank and differ (case/space-insensitive).
     *  A blank sheet value for this dimension means the column wasn't usable for this row — skip,
     *  don't manufacture a false mismatch. */
    private void addIfMismatch(List<String> mismatches, String label, String sheetVal, String dbVal) {
        if (sheetVal == null || sheetVal.isBlank()) return;
        String a = sheetVal.trim();
        String b = dbVal == null ? "" : dbVal.trim();
        if (!a.equalsIgnoreCase(b)) {
            mismatches.add(label);
        }
    }

    // ── Header detection & cell reading ────────────────────────────────────────

    private static final String HEADER_ROW_MARKER = "__headerRowNum__";

    /** Scans the first MAX_HEADER_SCAN_ROWS rows for one containing a cell matching COL_PSRN
     *  (case-insensitive, trimmed), builds a normalized-header-text → column-index map.
     *  Returns null if no such row is found. */
    private Map<String, Integer> findHeaderRow(Sheet sheet) {
        int lastRow = Math.min(sheet.getLastRowNum(), MAX_HEADER_SCAN_ROWS);
        for (int rowNum = 0; rowNum <= lastRow; rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) continue;
            Map<String, Integer> map = new HashMap<>();
            boolean foundPsrn = false;
            for (Cell cell : row) {
                String text = cellToString(cell).trim();
                if (text.isEmpty()) continue;
                map.put(norm(text), cell.getColumnIndex());
                if (norm(text).equals(norm(COL_PSRN))) foundPsrn = true;
            }
            if (foundPsrn) {
                map.put(HEADER_ROW_MARKER, rowNum);
                return map;
            }
        }
        return null;
    }

    private String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    private String cellToString(Row row, Integer colIndex) {
        if (row == null || colIndex == null) return "";
        return cellToString(row.getCell(colIndex));
    }

    /** Reads any cell type as plain text — numeric integral values are rendered without a
     *  trailing ".0" (important for PSRN / PEN No / Apaar ID, which must stay exact digit
     *  strings), formulas are evaluated to their cached value, blank/null cells return "". */
    private String cellToString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    return String.valueOf((long) d);
                }
                return String.valueOf(d);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue().trim();
                } catch (Exception e) {
                    try {
                        double fd = cell.getNumericCellValue();
                        return fd == Math.floor(fd) ? String.valueOf((long) fd) : String.valueOf(fd);
                    } catch (Exception e2) {
                        return "";
                    }
                }
            case BLANK:
            default:
                return "";
        }
    }
}
