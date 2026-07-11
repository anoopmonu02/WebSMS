package com.smsweb.sms.helper;

import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.student.StudentRegionalDetail;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class ExcelFileHandler {
    public String[] GRADE_HEADER = {"Medium","Grade","Section"};
    public String[] GRADE_HEADER_FULL = {"All Student List"};
    public String[] SR_SAMPLE_HEADER = {"Student Name","ID#","Father Name", "Mother Name","Mobile","SR No"};
    public String[] AADHAR_SAMPLE_HEADER = {"Student Name","ID#","Father Name", "Mother Name","Mobile","Aadhar No"};
    public String[] EXAM_RESULT_SAMPLE_HEADER = {"Student Name","ID#","Father Name", "Mother Name","Mobile","SR No","Exam Name", "Exam Result Date","Total Marks","Obtained Marks","Percentage(%)","Division","Result","Remark"};

    public ByteArrayInputStream LoadSampleSRFile(String fileName, List<AcademicStudent> list, String[] medium_grade_section, String fileType) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try{
            //Create sheet
            Sheet sheet = workbook.createSheet("Student List");

            Row row = sheet.createRow(0);
            int colCount = 0;
            if("F".equalsIgnoreCase(fileType)){
                for (int i=0;i<GRADE_HEADER_FULL.length;i++){
                    Cell keyCell = row.createCell(colCount);
                    keyCell.setCellValue(GRADE_HEADER_FULL[i]);
                }
            } else{
                for (int i=0;i<GRADE_HEADER.length;i++){
                    Cell keyCell = row.createCell(colCount);
                    keyCell.setCellValue(GRADE_HEADER[i]);
                    colCount++;
                    Cell valueCell = row.createCell(colCount);
                    valueCell.setCellValue(medium_grade_section[i]);
                    colCount++;
                }
            }

            //Create Main Header
            row = sheet.createRow(1);
            if("aadhar_file".equalsIgnoreCase(fileName)){
                createSingleRow(AADHAR_SAMPLE_HEADER, 1, row);
            } else if("G_marks_entry".equalsIgnoreCase(fileName)){
                createSingleRow(EXAM_RESULT_SAMPLE_HEADER, 1, row);
            } else{
                createSingleRow(SR_SAMPLE_HEADER, 1, row);
            }

            int rowCount = 2;
            //Writing Data
            /*for(AcademicStudent student: list){
                row = sheet.createRow(rowCount);
                colCount = 0;
                rowCount++;
                Cell dataCell = row.createCell(colCount); colCount++;
                dataCell.setCellValue(student.getStudent().getStudentName());
                dataCell = row.createCell(colCount); colCount++;
                dataCell.setCellValue(student.getUuid().toString());
                dataCell = row.createCell(colCount); colCount++;
                dataCell.setCellValue(student.getStudent().getFatherName());
                dataCell = row.createCell(colCount);  colCount++;
                dataCell.setCellValue(student.getStudent().getMotherName());
                dataCell = row.createCell(colCount);  colCount++;
                dataCell.setCellValue(student.getStudent().getMobile1());
                if("G_marks_entry".equalsIgnoreCase(fileName)){
                    dataCell = row.createCell(colCount);  colCount++;
                    dataCell.setCellValue(student.getClassSrNo());
                    dataCell = row.createCell(colCount);  colCount++;
                    dataCell.setCellValue("");
                    dataCell = row.createCell(colCount);  colCount++;
                    dataCell.setCellValue("");
                    dataCell = row.createCell(colCount);  colCount++;
                    dataCell.setCellValue("");
                    dataCell = row.createCell(colCount);  colCount++;
                    dataCell.setCellValue("");
                    dataCell = row.createCell(colCount);  colCount++;
                    dataCell.setCellValue("");
                    dataCell = row.createCell(colCount);  colCount++;
                    dataCell.setCellValue("");
                    dataCell = row.createCell(colCount);  colCount++;
                    dataCell.setCellValue("");
                    dataCell = row.createCell(colCount);  colCount++;
                    dataCell.setCellValue("");
                } else{
                    //if file is for SR or Aadhar result
                    dataCell = row.createCell(colCount);  colCount++;
                    dataCell.setCellValue("");
                }
            }*/
            for (AcademicStudent student : list) {
                row = sheet.createRow(rowCount++);
                colCount = 0;

                colCount = createCell(row, colCount, student.getStudent().getStudentName());
                colCount = createCell(row, colCount, student.getUuid().toString());
                colCount = createCell(row, colCount, student.getStudent().getFatherName());
                colCount = createCell(row, colCount, student.getStudent().getMotherName());
                colCount = createCell(row, colCount, student.getStudent().getMobile1());

                if ("G_marks_entry".equalsIgnoreCase(fileName)) {
                    colCount = createCell(row, colCount, student.getClassSrNo());
                    for (int i = 0; i < 9; i++) {
                        colCount = createCell(row, colCount, "");
                    }
                } else {
                    //if file is for SR or Aadhar result
                    colCount = createCell(row, colCount, "");
                }
            }
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());

        }catch(Exception e){
            e.printStackTrace();
            return null;
        }
        finally {
            workbook.close();
            out.flush();
            out.close();
        }
    }

    public Row createSingleRow(String[] rowData, int rowIndex, Row row){
        try{
            for(int i=0;i<rowData.length;i++){
                Cell cell = row.createCell(i);
                cell.setCellValue(rowData[i]);
            }
            return row;
        }catch(Exception e){
            return null;
        }
    }

    private int createCell(Row row, int colIndex, String value) {
        row.createCell(colIndex).setCellValue(value);
        return colIndex + 1;
    }

    public boolean checkValidExcelFormat(MultipartFile excelFile){
        try{
            String contentType = excelFile.getContentType();
            String fileName = excelFile.getOriginalFilename();

            if((fileName != null && (fileName.endsWith(".xls") || fileName.endsWith(".xlsx"))) &&
                    (contentType != null && (contentType.equals("application/vnd.ms-excel") ||
                            contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))){
                return true;
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return false;
    }
    public List<String[]> excelDataToList(InputStream inputStream, int dataStartRowNumber) throws IOException{
        List<String[]> excelData = new ArrayList<>();
        try{
            int rowNumber = 0;
            XSSFWorkbook workbook = new XSSFWorkbook(inputStream);
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> iterator = sheet.iterator();
            DataFormatter dataFormatter = new DataFormatter();
            while(iterator.hasNext()){
                Row row = iterator.next();
                if(rowNumber<dataStartRowNumber){
                    rowNumber++;
                    //check for header
                    continue;
                }

                Iterator<Cell> cellIterator = row.iterator();
                String[] rowData = new String[7];
                while(cellIterator.hasNext()){
                    Cell cell = cellIterator.next();
                    int colIdx = cell.getColumnIndex();
                    if (colIdx < rowData.length) {
                        rowData[colIdx] = dataFormatter.formatCellValue(cell);
                    }
                }
                excelData.add(rowData);
            }
            return excelData;
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Parses the Previous Pending Balance Excel template.
     * Expected columns (row 1 = header, data from row 2):
     *   0=SNo, 1=Student Name, 2=Father Name, 3=SR No, 4=Class, 5=Pending Amount
     * Returns each data row as String[6]: [sno, studentName, fatherName, srNo, className, pendingAmount]
     */
    public List<String[]> excelOpeningBalanceDataToList(InputStream inputStream) throws IOException {
        List<String[]> result = new ArrayList<>();
        try {
            XSSFWorkbook workbook = new XSSFWorkbook(inputStream);
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            int rowNum = 0;
            for (Row row : sheet) {
                if (rowNum++ == 0) continue; // skip header
                // skip empty / total rows — require at least SR No (col 3) and Pending Amount (col 5)
                Cell srCell  = row.getCell(3);
                Cell amtCell = row.getCell(5);
                if (srCell == null && amtCell == null) continue;
                String srVal = srCell  != null ? fmt.formatCellValue(srCell,  evaluator).trim() : "";
                String amVal = amtCell != null ? fmt.formatCellValue(amtCell, evaluator).trim() : "";
                if (srVal.isEmpty() || amVal.isEmpty()) continue;
                // skip rows where SR No is not numeric (e.g. "Total:" row)
                try { Double.parseDouble(amVal); } catch (NumberFormatException e) { continue; }

                String[] data = new String[6];
                data[0] = row.getCell(0) != null ? fmt.formatCellValue(row.getCell(0), evaluator).trim() : "";
                data[1] = row.getCell(1) != null ? fmt.formatCellValue(row.getCell(1), evaluator).trim() : "";
                data[2] = row.getCell(2) != null ? fmt.formatCellValue(row.getCell(2), evaluator).trim() : "";
                data[3] = srVal;
                data[4] = row.getCell(4) != null ? fmt.formatCellValue(row.getCell(4), evaluator).trim() : "";
                data[5] = amVal;
                result.add(data);
            }
            workbook.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public List<String[]> excelExamResultDataToList(InputStream inputStream, int dataStartRowNumber) throws IOException{
        List<String[]> excelData = new ArrayList<>();
        try{
            int rowNumber = 0;
            XSSFWorkbook workbook = new XSSFWorkbook(inputStream);
            Sheet sheet = workbook.getSheetAt(0);
            // FormulaEvaluator computes formula cell values instead of returning the formula string
            org.apache.poi.ss.usermodel.FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            DataFormatter dataFormatter = new DataFormatter();
            Iterator<Row> iterator = sheet.iterator();
            while(iterator.hasNext()){
                Row row = iterator.next();
                if(rowNumber < dataStartRowNumber){
                    rowNumber++;
                    continue;
                }

                Iterator<Cell> cellIterator = row.iterator();
                String[] rowData = new String[15];
                if(row.getPhysicalNumberOfCells() > 10){
                    while(cellIterator.hasNext()){
                        Cell cell = cellIterator.next();
                        int colIdx = cell.getColumnIndex();
                        if (colIdx < rowData.length) {
                            // Use evaluator so formula cells return computed value, not formula string
                            rowData[colIdx] = dataFormatter.formatCellValue(cell, evaluator);
                        }
                    }
                    excelData.add(rowData);
                }
            }
            workbook.close();
            return excelData;
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Student Regional-Language (Hindi) Details — download template / parse upload
    // ─────────────────────────────────────────────────────────────────────────

    public static final String[] REGIONAL_HEADER = {
            "SNo", "UUID", "Student Name", "Father Name", "Mother Name", "Address",
            "Student Name (Regional)", "Father Name (Regional)", "Mother Name (Regional)", "Address (Regional)"
    };

    /**
     * Builds the downloadable Excel template — pre-filled with each student's real
     * data plus their existing regional-language values (if any already saved).
     *
     * Only the last 4 columns are left editable: the sheet is protected and every
     * other cell is explicitly locked, so Excel itself refuses edits to the
     * English/uuid columns. The UUID column is also hidden — it exists purely as
     * the match-key used on re-upload and is never meant to be edited by hand.
     * (The server independently ignores columns 0,2-5 on upload regardless, so
     * this Excel-level protection is defense-in-depth, not the sole guard.)
     */
    public ByteArrayInputStream buildStudentRegionalTemplate(List<Student> students,
                                                              Map<Long, StudentRegionalDetail> existingByStudentId,
                                                              String schoolName,
                                                              String sessionFormat) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Sheet sheet = workbook.createSheet("Regional Details");

            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 12);
            titleStyle.setFont(titleFont);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle lockedStyle = workbook.createCellStyle();
            lockedStyle.setLocked(true);
            lockedStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            lockedStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            lockedStyle.setBorderBottom(BorderStyle.THIN);
            lockedStyle.setBorderTop(BorderStyle.THIN);
            lockedStyle.setBorderLeft(BorderStyle.THIN);
            lockedStyle.setBorderRight(BorderStyle.THIN);

            CellStyle editableStyle = workbook.createCellStyle();
            editableStyle.setLocked(false);
            editableStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            editableStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            editableStyle.setBorderBottom(BorderStyle.THIN);
            editableStyle.setBorderTop(BorderStyle.THIN);
            editableStyle.setBorderLeft(BorderStyle.THIN);
            editableStyle.setBorderRight(BorderStyle.THIN);

            // Row 0 — title / context line
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("School: " + (schoolName == null ? "" : schoolName)
                    + "   |   Session: " + (sessionFormat == null ? "" : sessionFormat)
                    + "   |   Fill ONLY the yellow columns (Regional Language). Do not edit or reorder other columns.");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, REGIONAL_HEADER.length - 1));

            // Row 1 — column headers
            Row headerRow = sheet.createRow(1);
            for (int i = 0; i < REGIONAL_HEADER.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(REGIONAL_HEADER[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            int rowNum = 2;
            int sno = 1;
            for (Student student : students) {
                Row row = sheet.createRow(rowNum++);
                StudentRegionalDetail existing = existingByStudentId.get(student.getId());

                setCell(row, 0, String.valueOf(sno++), lockedStyle);
                setCell(row, 1, student.getUuid() != null ? student.getUuid().toString() : "", lockedStyle);
                setCell(row, 2, student.getStudentName(), lockedStyle);
                setCell(row, 3, student.getFatherName(), lockedStyle);
                setCell(row, 4, student.getMotherName(), lockedStyle);
                setCell(row, 5, buildFullAddress(student), lockedStyle);
                setCell(row, 6, existing != null ? existing.getStudentNameRegional() : "", editableStyle);
                setCell(row, 7, existing != null ? existing.getFatherNameRegional() : "", editableStyle);
                setCell(row, 8, existing != null ? existing.getMotherNameRegional() : "", editableStyle);
                setCell(row, 9, existing != null ? existing.getAddressRegional() : "", editableStyle);
            }

            for (int i = 0; i < REGIONAL_HEADER.length; i++) {
                sheet.autoSizeColumn(i);
            }
            // Hide the UUID column — it's the match-key, not meant for manual editing.
            sheet.setColumnHidden(1, true);

            // Protect the sheet: locked cells (default + our explicit lockedStyle) become
            // read-only in Excel; only cells styled with editableStyle (locked=false) stay editable.
            sheet.protectSheet("UAIC-Regional-2026");

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        } finally {
            workbook.close();
            out.flush();
            out.close();
        }
    }

    private void setCell(Row row, int colIndex, String value, CellStyle style) {
        Cell cell = row.createCell(colIndex);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    /**
     * Full reference address for the regional-language template — street address plus
     * city + state (province) appended, so whoever fills in the translated column has
     * the complete address to work from, not just the free-text street portion.
     * Skips appending city/province if that name is already present somewhere in the
     * free-text address (common when the address was originally typed as
     * "village, district") to avoid showing the same place name twice.
     */
    private String buildFullAddress(Student student) {
        if (student == null) return "";
        String rawAddress = student.getAddress() != null ? student.getAddress().trim() : "";
        String addressLower = rawAddress.toLowerCase();
        StringBuilder sb = new StringBuilder(rawAddress);

        if (student.getCity() != null && student.getCity().getCityName() != null && !student.getCity().getCityName().isBlank()) {
            String cityName = student.getCity().getCityName().trim();
            if (!addressLower.contains(cityName.toLowerCase())) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(cityName);
            }
        }
        if (student.getProvince() != null && student.getProvince().getProvinceName() != null && !student.getProvince().getProvinceName().isBlank()) {
            String provinceName = student.getProvince().getProvinceName().trim();
            if (!addressLower.contains(provinceName.toLowerCase())) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(provinceName);
            }
        }
        return sb.toString();
    }

    /**
     * Parses an uploaded Regional-Language template. Reads ONLY the UUID (match key,
     * column 1) and the 4 regional columns (6-9) — the English/locked columns are
     * never read back, so nothing typed there can affect the import even if a user
     * bypassed Excel's sheet protection.
     *
     * Regional cells are read via their CACHED formula result, never by asking POI to
     * re-evaluate the formula. This matters specifically for the common case of a Google
     * Sheets =GOOGLETRANSLATE(...) formula downloaded as .xlsx: Sheets exports it as
     * =IFERROR(__xludf.DUMMYFUNCTION("GOOGLETRANSLATE(...)"), "the real translated text"),
     * and critically ALSO writes that real translated text as the cell's cached value.
     * Asking POI to evaluate the formula fails (it doesn't know __xludf.DUMMYFUNCTION),
     * but reading the cached result directly recovers the real value with no evaluation
     * involved at all — exactly what's needed here.
     *
     * Returns each row as String[6]: [uuid, studentNameRegional, fatherNameRegional,
     * motherNameRegional, addressRegional, "true"/"" (whether any value above still looked
     * unusable even after reading the cached result, and was cleared as a precaution)]
     */
    public List<String[]> parseStudentRegionalUpload(InputStream inputStream) throws IOException {
        List<String[]> result = new ArrayList<>();
        XSSFWorkbook workbook = new XSSFWorkbook(inputStream);
        try {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            int rowNum = 0;
            for (Row row : sheet) {
                if (rowNum++ < 2) continue; // skip title row + header row
                Cell uuidCell = row.getCell(1);
                String uuid = uuidCell != null ? fmt.formatCellValue(uuidCell).trim() : "";
                if (uuid.isEmpty()) continue; // blank/trailing row

                boolean flagged = false;
                String[] data = new String[6];
                data[0] = uuid;
                for (int i = 0; i < 4; i++) {
                    String raw = cachedCellText(row, 6 + i, fmt);
                    if (isFormulaArtifact(raw)) {
                        flagged = true;
                        raw = "";
                    }
                    data[i + 1] = raw;
                }
                data[5] = flagged ? "true" : "";
                result.add(data);
            }
        } finally {
            workbook.close();
        }
        return result;
    }

    /**
     * Reads a cell's value WITHOUT ever asking POI to evaluate a formula. For a formula
     * cell this reads the last-CACHED result exactly as stored in the file (via
     * getCachedFormulaResultType() + the matching typed getter) — this is what recovers
     * the real value out of a Google Sheets GOOGLETRANSLATE export, since the formula
     * itself can't be evaluated but the cached result sitting next to it is real.
     */
    private String cachedCellText(Row row, int colIndex, DataFormatter fmt) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) return "";
        try {
            if (cell.getCellType() == CellType.FORMULA) {
                CellType cachedType = cell.getCachedFormulaResultType();
                switch (cachedType) {
                    case STRING:
                        return cell.getStringCellValue().trim();
                    case NUMERIC:
                        double num = cell.getNumericCellValue();
                        return (num == Math.floor(num)) ? String.valueOf((long) num) : String.valueOf(num);
                    case BOOLEAN:
                        return String.valueOf(cell.getBooleanCellValue());
                    default:
                        return ""; // ERROR / BLANK cached result — nothing usable
                }
            }
            return fmt.formatCellValue(cell).trim();
        } catch (Exception e) {
            return "";
        }
    }

    /** Detects broken/unconverted spreadsheet formulas — text that should never be saved as a real value. */
    private boolean isFormulaArtifact(String value) {
        if (value == null || value.isEmpty()) return false;
        String v = value.trim();
        if (v.startsWith("=")) return true;
        String upper = v.toUpperCase();
        if (upper.contains("_XLUDF") || upper.contains("DUMMYFUNCTION")) return true;
        return upper.equals("#N/A") || upper.equals("#REF!") || upper.equals("#VALUE!")
                || upper.equals("#NAME?") || upper.equals("#NULL!") || upper.equals("#DIV/0!")
                || upper.equals("#NUM!") || upper.equals("#ERROR!");
    }

}
