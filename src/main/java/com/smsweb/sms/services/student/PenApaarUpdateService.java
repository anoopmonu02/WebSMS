package com.smsweb.sms.services.student;

import com.smsweb.sms.dto.PenApaarUpdatePreviewResult;
import com.smsweb.sms.dto.PenApaarUpdateRow;
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
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * PEN-No-matched bulk update of Student.apaarId from an uploaded sheet.
 *
 * Match key is PEN No alone (see StudentRepository.findAllByPenNo — PEN No has no DB
 * uniqueness constraint, so a match can legitimately be ambiguous). Student Name / Gender /
 * DOB / Mobile / Class / Section are shown on the preview screen purely as read-only context
 * — they are never used to validate or block a match, because that data can be misspelled or
 * stale in a way PEN No is not.
 *
 * Fully separate from PsrnBulkUpdateService / StudentImportController — no shared code path.
 * Only the Apaar ID field is ever written by this service.
 */
@Service
public class PenApaarUpdateService {

    private static final Logger log = LoggerFactory.getLogger(PenApaarUpdateService.class);

    // Header text (case-insensitive, trimmed) expected in the sheet.
    private static final String COL_PEN_NO  = "PEN No";
    private static final String COL_APAAR   = "Apaar ID";
    private static final String COL_NAME    = "Student Name";
    private static final String COL_GENDER  = "Gender";
    private static final String COL_DOB     = "DOB";
    private static final String COL_MOBILE  = "Mobile";
    private static final String COL_CLASS   = "Class";
    private static final String COL_SECTION = "Section";

    private static final int MAX_HEADER_SCAN_ROWS = 10;

    private static final DateTimeFormatter DOB_DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final StudentRepository studentRepository;
    private final AcademicStudentRepository academicStudentRepository;
    private final UserService userService;

    public PenApaarUpdateService(StudentRepository studentRepository,
                                  AcademicStudentRepository academicStudentRepository,
                                  UserService userService) {
        this.studentRepository = studentRepository;
        this.academicStudentRepository = academicStudentRepository;
        this.userService = userService;
    }

    /** Step 2 — parse + validate, no DB writes. */
    public PenApaarUpdatePreviewResult parseAndValidate(byte[] fileBytes) {
        log.info("Inside parseAndValidate");
        PenApaarUpdatePreviewResult result = new PenApaarUpdatePreviewResult();
        result.setRows(parseRows(fileBytes, result.getMissingOptionalColumns()));
        result.recalcCounts();
        return result;
    }

    /** Step 3 — re-parses the file fresh (never trusts the session-stored preview object for
     *  the actual DB mutation, same defensive pattern as PsrnBulkUpdateService.executeUpdate)
     *  and saves every row that's READY. */
    @Transactional
    public PenApaarUpdatePreviewResult executeUpdate(byte[] fileBytes) {
        log.info("Inside executeUpdate");
        PenApaarUpdatePreviewResult result = new PenApaarUpdatePreviewResult();
        List<PenApaarUpdateRow> rows = parseRows(fileBytes, result.getMissingOptionalColumns());
        result.setRows(rows);

        UserEntity loggedInUser = userService.getLoggedInUser();

        for (PenApaarUpdateRow row : rows) {
            if (!PenApaarUpdateRow.STATUS_READY.equals(row.getStatus())) continue;

            try {
                Student student = studentRepository.findById(row.getStudentId())
                        .orElseThrow(() -> new IllegalStateException("Student no longer exists"));
                student.setApaarId(row.getSheetApaarId());
                student.setUpdatedBy(loggedInUser);
                studentRepository.save(student);
                row.setStatus(PenApaarUpdateRow.STATUS_UPDATED);
                row.setMessage("Updated successfully.");
            } catch (Exception e) {
                // Caught per-row so one bad row can't abort the whole batch or roll back
                // rows already saved earlier in the loop.
                log.error("Failed to save Apaar ID row {} (penNo={})", row.getRowNum(), row.getPenNoRaw(), e);
                row.setStatus(PenApaarUpdateRow.STATUS_ERROR);
                row.setMessage("Save failed: " + e.getMessage());
            }
        }

        result.recalcCounts();
        return result;
    }

    // ── Parsing ──────────────────────────────────────────────────────────────

    private List<PenApaarUpdateRow> parseRows(byte[] fileBytes, List<String> missingOptionalColumnsOut) {
        List<PenApaarUpdateRow> rows = new ArrayList<>();

        try (InputStream is = new ByteArrayInputStream(fileBytes);
             Workbook wb = WorkbookFactory.create(is)) {

            Sheet sheet = wb.getSheetAt(0);

            Map<String, Integer> headerIndex = findHeaderRow(sheet);
            if (headerIndex == null) {
                throw new IllegalArgumentException("Could not find a header row containing '" + COL_PEN_NO + "' in the first " + MAX_HEADER_SCAN_ROWS + " rows.");
            }
            int headerRowNum = headerIndex.remove(HEADER_ROW_MARKER);

            Integer penCol = headerIndex.get(norm(COL_PEN_NO));
            Integer apaarCol = headerIndex.get(norm(COL_APAAR));

            if (penCol == null) {
                throw new IllegalArgumentException("Sheet is missing the required '" + COL_PEN_NO + "' column.");
            }
            if (apaarCol == null) {
                throw new IllegalArgumentException("Sheet is missing the required '" + COL_APAAR + "' column.");
            }

            Integer nameCol = headerIndex.get(norm(COL_NAME));
            Integer genderCol = headerIndex.get(norm(COL_GENDER));
            Integer dobCol = headerIndex.get(norm(COL_DOB));
            Integer mobileCol = headerIndex.get(norm(COL_MOBILE));
            Integer classCol = headerIndex.get(norm(COL_CLASS));
            Integer sectionCol = headerIndex.get(norm(COL_SECTION));

            if (nameCol == null) missingOptionalColumnsOut.add(COL_NAME);
            if (genderCol == null) missingOptionalColumnsOut.add(COL_GENDER);
            if (dobCol == null) missingOptionalColumnsOut.add(COL_DOB);
            if (mobileCol == null) missingOptionalColumnsOut.add(COL_MOBILE);
            if (classCol == null) missingOptionalColumnsOut.add(COL_CLASS);
            if (sectionCol == null) missingOptionalColumnsOut.add(COL_SECTION);

            for (Row row : sheet) {
                if (row.getRowNum() <= headerRowNum) continue;

                String penRaw = cellToString(row, penCol);
                String apaarRaw = cellToString(row, apaarCol);

                // Fully blank row (no PEN No at all) — not an error, just not present. Skip silently.
                if (penRaw.isBlank() && apaarRaw.isBlank()
                        && cellToString(row, nameCol).isBlank()) {
                    continue;
                }

                PenApaarUpdateRow r = new PenApaarUpdateRow();
                r.setRowNum(row.getRowNum() + 1);
                r.setPenNoRaw(penRaw);
                r.setSheetApaarId(apaarRaw.trim());
                r.setSheetStudentName(cellToString(row, nameCol));
                r.setSheetGender(cellToString(row, genderCol));
                r.setSheetDob(cellToString(row, dobCol));
                r.setSheetMobile(cellToString(row, mobileCol));
                r.setSheetClass(cellToString(row, classCol));
                r.setSheetSection(cellToString(row, sectionCol));

                processRow(r);
                rows.add(r);
            }

        } catch (IllegalArgumentException iae) {
            throw iae; // structural errors (missing columns) surface as-is to the controller
        } catch (Exception e) {
            log.error("Failed to parse Apaar ID update sheet", e);
            throw new RuntimeException("Failed to parse Excel file: " + e.getMessage(), e);
        }

        return rows;
    }

    private void processRow(PenApaarUpdateRow r) {
        // 1. PEN No must be present.
        String penNo = r.getPenNoRaw() == null ? "" : r.getPenNoRaw().trim().replaceAll("\\.0$", "");
        if (penNo.isBlank()) {
            r.setStatus(PenApaarUpdateRow.STATUS_ERROR);
            r.setMessage("PEN No is blank.");
            return;
        }
        if (!penNo.matches("[0-9]{11}")) {
            r.setStatus(PenApaarUpdateRow.STATUS_ERROR);
            r.setMessage("Invalid PEN No format (must be 11 digits) — got '" + r.getPenNoRaw() + "'.");
            return;
        }

        // 2. Apaar ID: blank means "leave for blank data" — skip, not an error.
        if (r.getSheetApaarId() == null || r.getSheetApaarId().isBlank()) {
            r.setStatus(PenApaarUpdateRow.STATUS_SKIP_BLANK);
            r.setMessage("Sheet Apaar ID is blank — left unchanged.");
            return;
        }

        // 3. Format validation, same pattern as Student.apaarId.
        if (!r.getSheetApaarId().matches("[0-9]{12}")) {
            r.setStatus(PenApaarUpdateRow.STATUS_ERROR);
            r.setMessage("Invalid Apaar ID format (must be 12 digits) — got '" + r.getSheetApaarId() + "'.");
            return;
        }

        // 4. Match the student by PEN No. No DB uniqueness constraint on penNo, so this can
        //    legitimately return more than one row — never guess which one to update.
        List<Student> matches = studentRepository.findAllByPenNo(penNo);
        if (matches.isEmpty()) {
            r.setStatus(PenApaarUpdateRow.STATUS_ERROR);
            r.setMessage("No student found with PEN No " + penNo + ".");
            return;
        }
        if (matches.size() > 1) {
            r.setStatus(PenApaarUpdateRow.STATUS_CONFLICT);
            r.setMessage("PEN No " + penNo + " matches " + matches.size() + " students — ambiguous, flagged for manual review, not updated.");
            return;
        }

        Student student = matches.get(0);
        r.setStudentId(student.getId());
        r.setDbStudentName(student.getStudentName());
        r.setDbGender(student.getGender());
        r.setDbDob(student.getDob() != null ? student.getDob().format(DOB_DISPLAY_FORMAT) : null);
        r.setDbMobile(student.getMobile1());

        // Class/Section MUST come from the student's current (isMigrated=false) AcademicStudent
        // enrollment row, never from Student.grade/Student.section directly — those two fields
        // are only an admission-time snapshot on the Student entity (see PsrnBulkUpdateService
        // for the same reasoning). Read-only context here, never affects status.
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
            log.warn("Could not resolve current enrollment for student {} (penNo={}), falling back to Student.grade/section", student.getId(), penNo, e);
        }
        if (currentGrade == null) currentGrade = student.getGrade() != null ? student.getGrade().getGradeName() : null;
        if (currentSection == null) currentSection = student.getSection() != null ? student.getSection().getSectionName() : null;

        r.setDbClass(currentGrade);
        r.setDbSection(currentSection);

        String currentValue = student.getApaarId();
        r.setDbApaarId(currentValue);

        // 5. Conflict check — an existing, different value is never auto-overwritten.
        if (currentValue != null && !currentValue.isBlank()) {
            if (currentValue.trim().equalsIgnoreCase(r.getSheetApaarId())) {
                r.setStatus(PenApaarUpdateRow.STATUS_SKIP_ALREADY_SET);
                r.setMessage("Already set to this value.");
            } else {
                r.setStatus(PenApaarUpdateRow.STATUS_CONFLICT);
                r.setMessage("DB already has a different Apaar ID (" + currentValue + ") — flagged for manual review, not updated.");
            }
            return;
        }

        r.setStatus(PenApaarUpdateRow.STATUS_READY);
        r.setMessage("Ready to update.");
    }

    // ── Header detection & cell reading ────────────────────────────────────────

    private static final String HEADER_ROW_MARKER = "__headerRowNum__";

    /** Scans the first MAX_HEADER_SCAN_ROWS rows for one containing a cell matching COL_PEN_NO
     *  (case-insensitive, trimmed), builds a normalized-header-text → column-index map.
     *  Returns null if no such row is found. */
    private Map<String, Integer> findHeaderRow(Sheet sheet) {
        int lastRow = Math.min(sheet.getLastRowNum(), MAX_HEADER_SCAN_ROWS);
        for (int rowNum = 0; rowNum <= lastRow; rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) continue;
            Map<String, Integer> map = new HashMap<>();
            boolean foundPenNo = false;
            for (Cell cell : row) {
                String text = cellToString(cell).trim();
                if (text.isEmpty()) continue;
                map.put(norm(text), cell.getColumnIndex());
                if (norm(text).equals(norm(COL_PEN_NO))) foundPenNo = true;
            }
            if (foundPenNo) {
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
     *  trailing ".0" (important for PEN No / Apaar ID, which must stay exact digit strings),
     *  formulas are evaluated to their cached value, blank/null cells return "". */
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
