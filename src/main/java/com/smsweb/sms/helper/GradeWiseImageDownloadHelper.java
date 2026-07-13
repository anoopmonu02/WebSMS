package com.smsweb.sms.helper;

import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the "Grade-wise Images Download" zip for the Student module: one zip containing
 * every selected student's photo (renamed "{seq}_{STUDENT NAME}_{FATHER NAME}.ext" so it can
 * be matched 1:1 against the Excel "Sequence No" column) plus a single Excel sheet listing
 * every student in the selection.
 *
 * Read-only against the existing flat student-image folder — only ever reads/copies bytes
 * from it, never writes, deletes, or renames anything there. This class is not referenced
 * by any existing feature, so it carries zero regression risk to current functionality.
 *
 * Ordering: students with a photo on disk are listed first (sorted by student name) and
 * numbered 1..N — that number is both the Excel "Sequence No" and the filename prefix.
 * Students with no photo (or whose stored filename no longer exists on disk) are appended
 * at the bottom, sorted by name, with "Sequence No" left blank since there's no file to
 * match them to.
 */
@Component
public class GradeWiseImageDownloadHelper {

    private static final Logger log = LoggerFactory.getLogger(GradeWiseImageDownloadHelper.class);

    private static final String[] HEADER = {
            "Student Name", "Class-Section", "SR", "Father Name", "Mother Name",
            "Address", "Mobile", "Sequence No"
    };

    private static final String DATE_PATTERN = "ddMMyyyy";

    /** "{grade}_{section}_{ddMMyyyy}.zip" — sanitized so it's always a safe filename. */
    public String buildZipFileName(String gradeName, String sectionName) {
        return baseFolderName(gradeName, sectionName) + ".zip";
    }

    private String baseFolderName(String gradeName, String sectionName) {
        String date = new SimpleDateFormat(DATE_PATTERN).format(new Date());
        return sanitize(gradeName) + "_" + sanitize(sectionName) + "_" + date;
    }

    /** Result of a zip build: the zip bytes plus how many students landed in each group, for UI messaging. */
    public static class ZipBuildResult {
        public final byte[] zipBytes;
        public final int withImageCount;
        public final int withoutImageCount;

        public ZipBuildResult(byte[] zipBytes, int withImageCount, int withoutImageCount) {
            this.zipBytes = zipBytes;
            this.withImageCount = withImageCount;
            this.withoutImageCount = withoutImageCount;
        }
    }

    /**
     * @param students              academic students already filtered to one medium/grade/section (Active only)
     * @param gradeName             for the "Class-Section" column + folder/file naming
     * @param sectionName           for the "Class-Section" column + folder/file naming
     * @param studentImageDirectory the existing flat folder student photos are stored in (read-only)
     */
    public ZipBuildResult buildDownloadZip(List<AcademicStudent> students, String gradeName, String sectionName,
                                    String studentImageDirectory) throws IOException {

        String classSection = gradeName + " - " + sectionName;
        String folderName = baseFolderName(gradeName, sectionName);

        List<AcademicStudent> withImage = new ArrayList<>();
        List<AcademicStudent> withoutImage = new ArrayList<>();
        for (AcademicStudent as : students) {
            String pic = as.getStudent() != null ? as.getStudent().getPic() : null;
            if (pic != null && !pic.isBlank() && Files.exists(Paths.get(studentImageDirectory, pic))) {
                withImage.add(as);
            } else {
                withoutImage.add(as);
            }
        }

        Comparator<AcademicStudent> byStudentName = Comparator.comparing(as ->
                (as.getStudent() != null && as.getStudent().getStudentName() != null)
                        ? as.getStudent().getStudentName() : "");
        withImage.sort(byStudentName);
        withoutImage.sort(byStudentName);

        // Internal match key is the AcademicStudent DB id — never exposed in the filename/sheet,
        // used purely so the zip-entry writing loop and the Excel-writing loop agree on the
        // same sequence number and filename for the same student.
        Map<Long, Integer> seqByAcademicStudentId = new LinkedHashMap<>();
        Map<Long, String> imageFileNameByAcademicStudentId = new LinkedHashMap<>();
        int seq = 1;
        for (AcademicStudent as : withImage) {
            Student s = as.getStudent();
            String ext = extensionOf(s.getPic());
            String fileName = seq + "_" + sanitize(s.getStudentName()) + "_" + sanitize(s.getFatherName()) + ext;
            seqByAcademicStudentId.put(as.getId(), seq);
            imageFileNameByAcademicStudentId.put(as.getId(), fileName);
            seq++;
        }

        ByteArrayOutputStream zipBytesOut = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipBytesOut)) {

            for (AcademicStudent as : withImage) {
                String originalPic = as.getStudent().getPic();
                Path source = Paths.get(studentImageDirectory, originalPic);
                String entryName = folderName + "/" + imageFileNameByAcademicStudentId.get(as.getId());
                boolean entryOpened = false;
                try {
                    zos.putNextEntry(new ZipEntry(entryName));
                    entryOpened = true;
                    Files.copy(source, zos);
                } catch (IOException e) {
                    // Source file vanished between the exists() check and now (rare race), or the
                    // entry itself couldn't be opened — skip this one image rather than fail the
                    // whole download.
                    log.warn("Skipping image for academicStudentId={} — could not read {}", as.getId(), source, e);
                } finally {
                    if (entryOpened) {
                        try {
                            zos.closeEntry();
                        } catch (IOException ignore) {
                            // Already logged above if the copy itself failed; nothing more to do.
                        }
                    }
                }
            }

            byte[] excelBytes = buildExcel(withImage, withoutImage, classSection, seqByAcademicStudentId);
            zos.putNextEntry(new ZipEntry(folderName + "/" + folderName + "_StudentList.xlsx"));
            zos.write(excelBytes);
            zos.closeEntry();
        }
        return new ZipBuildResult(zipBytesOut.toByteArray(), withImage.size(), withoutImage.size());
    }

    private byte[] buildExcel(List<AcademicStudent> withImage, List<AcademicStudent> withoutImage,
                               String classSection, Map<Long, Integer> seqByAcademicStudentId) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Student List");

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
            for (AcademicStudent as : withImage) {
                rowNum = writeRow(sheet, rowNum, as, classSection, String.valueOf(seqByAcademicStudentId.get(as.getId())));
            }
            for (AcademicStudent as : withoutImage) {
                rowNum = writeRow(sheet, rowNum, as, classSection, "-");
            }

            for (int i = 0; i < HEADER.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private int writeRow(Sheet sheet, int rowNum, AcademicStudent as, String classSection, String seqValue) {
        Student s = as.getStudent();
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(nullSafe(s.getStudentName()));
        row.createCell(1).setCellValue(classSection);
        row.createCell(2).setCellValue(nullSafe(as.getClassSrNo()));
        row.createCell(3).setCellValue(nullSafe(s.getFatherName()));
        row.createCell(4).setCellValue(nullSafe(s.getMotherName()));
        row.createCell(5).setCellValue(nullSafe(s.getAddress()));
        row.createCell(6).setCellValue(nullSafe(s.getMobile1()));
        row.createCell(7).setCellValue(seqValue);
        return rowNum + 1;
    }

    private String nullSafe(String v) {
        return v == null ? "" : v;
    }

    private String extensionOf(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot) : "";
    }

    /** Filesystem-safe token: letters/digits only, separated by single underscores. */
    private String sanitize(String value) {
        if (value == null) return "";
        String cleaned = value.trim().replaceAll("[^A-Za-z0-9]+", "_");
        cleaned = cleaned.replaceAll("_+", "_");
        if (cleaned.startsWith("_")) cleaned = cleaned.substring(1);
        if (cleaned.endsWith("_")) cleaned = cleaned.substring(0, cleaned.length() - 1);
        return cleaned;
    }
}
