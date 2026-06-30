package com.smsweb.sms.services.fees;

import com.smsweb.sms.dto.OpeningBalanceRowDto;
import com.smsweb.sms.helper.ExcelFileHandler;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class OpeningBalanceService {

    private static final Logger log = LoggerFactory.getLogger(OpeningBalanceService.class);

    private final ExcelFileHandler excelFileHandler;
    private final AcademicStudentRepository academicStudentRepository;

    public OpeningBalanceService(ExcelFileHandler excelFileHandler,
                                 AcademicStudentRepository academicStudentRepository) {
        this.excelFileHandler = excelFileHandler;
        this.academicStudentRepository = academicStudentRepository;
    }

    /**
     * Match logic (in order):
     *  1. Student name (LIKE, case-insensitive) + grade + section + school + AY → MATCHED if exactly 1
     *  2. Father name  (LIKE, case-insensitive) + grade + section + school + AY → NAME_MISMATCH if exactly 1
     *  3. Neither found or multiple ambiguous → SR_NOT_FOUND
     */
    public List<OpeningBalanceRowDto> preview(MultipartFile file, Long schoolId, Long academicYearId) {
        List<OpeningBalanceRowDto> rows = new ArrayList<>();
        try {
            List<String[]> rawData = excelFileHandler.excelOpeningBalanceDataToList(file.getInputStream());
            if (rawData == null || rawData.isEmpty()) return rows;

            int rowNum = 2;
            for (String[] data : rawData) {

                // Skip blank / totals rows
                if (data[1] == null || data[1].trim().isEmpty()) {
                    rowNum++;
                    continue;
                }

                OpeningBalanceRowDto dto = new OpeningBalanceRowDto();
                dto.setRowNum(rowNum++);
                dto.setStudentName(data[1].trim());
                dto.setFatherName(data[2] != null ? data[2].trim() : "");
                dto.setSrNo(data[3]);
                dto.setExcelClass(data[4]);

                String[] gradeSec = parseGradeSection(data[4]);
                dto.setExcelGrade(gradeSec[0]);
                dto.setExcelSection(gradeSec[1]);

                try {
                    dto.setPendingAmount(new BigDecimal(data[5].replace(",", "")));
                } catch (NumberFormatException e) {
                    dto.setPendingAmount(BigDecimal.ZERO);
                }

                // ── Step 1: exact student name + grade + section + school + AY ─
                List<AcademicStudent> byName = academicStudentRepository
                        .findActiveByStudentNameAndGradeAndSection(
                                dto.getStudentName(), schoolId, academicYearId,
                                dto.getExcelGrade(), dto.getExcelSection());

                if (!byName.isEmpty()) {
                    populate(dto, byName.get(0), "MATCHED");
                    rows.add(dto);
                    continue;
                }

                // ── Step 2: exact father name + grade + section + school + AY ─
                /*if (dto.getFatherName() != null && !dto.getFatherName().isEmpty()) {
                    List<AcademicStudent> byFather = academicStudentRepository
                            .findActiveByFatherNameAndGradeAndSection(
                                    dto.getFatherName(), schoolId, academicYearId,
                                    dto.getExcelGrade(), dto.getExcelSection());

                    if (!byFather.isEmpty()) {
                        populate(dto, byFather.get(0), "NAME_MISMATCH");
                        rows.add(dto);
                        continue;
                    }
                }*/

                // ── Step 3: no match found ────────────────────────────────────
                dto.setMatchStatus("SR_NOT_FOUND");
                rows.add(dto);
            }

        } catch (Exception e) {
            log.error("Opening balance preview error", e);
            throw new RuntimeException("Failed to parse file: " + e.getMessage(), e);
        }
        return rows;
    }

    /**
     * Save opening balances.
     * Idempotent — skips rows where openingBalance is already > 0.
     */
    @Transactional
    public int[] save(List<Long> academicStudentIds, List<String> amounts, List<String> remarks) {
        int saved = 0, skipped = 0, errors = 0;
        for (int i = 0; i < academicStudentIds.size(); i++) {
            Long asId = academicStudentIds.get(i);
            if (asId == null) { skipped++; continue; }
            try {
                AcademicStudent as = academicStudentRepository.findById(asId).orElse(null);
                if (as == null) { skipped++; continue; }

                // Idempotency: skip if balance already set
                if (as.getOpeningBalance() != null && as.getOpeningBalance().compareTo(BigDecimal.ZERO) > 0) {
                    log.info("SKIP asId={} — openingBalance already set to {}", asId, as.getOpeningBalance());
                    skipped++;
                    continue;
                }

                BigDecimal amount = new BigDecimal(amounts.get(i).replace(",", ""));
                as.setOpeningBalance(amount);
                if (remarks != null && i < remarks.size() && remarks.get(i) != null) {
                    as.setOpeningBalanceRemark(remarks.get(i));
                }
                academicStudentRepository.save(as);
                saved++;
            } catch (Exception e) {
                log.error("Error saving opening balance for academicStudentId={}", asId, e);
                errors++;
            }
        }
        return new int[]{saved, skipped, errors};
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void populate(OpeningBalanceRowDto dto, AcademicStudent as, String status) {
        dto.setAcademicStudentId(as.getId());
        dto.setSystemStudentName(as.getStudent().getStudentName());
        dto.setSystemFatherName(as.getStudent().getFatherName());
        dto.setSystemGrade(as.getGrade().getGradeName());
        dto.setSystemSection(as.getSection().getSectionName());
        dto.setMatchStatus(status);
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    /**
     * "11 SCI V" → ["11 SCI", "V"],  "10 G1" → ["10", "G1"]
     * Last token = section, everything before = grade.
     */
    private String[] parseGradeSection(String classStr) {
        if (classStr == null || classStr.trim().isEmpty()) return new String[]{"", ""};
        String[] tokens = classStr.trim().split("\\s+");
        if (tokens.length == 1) return new String[]{tokens[0], ""};
        String section = tokens[tokens.length - 1];
        String grade   = classStr.trim().substring(0, classStr.trim().lastIndexOf(section)).trim();
        return new String[]{grade, section};
    }
}
