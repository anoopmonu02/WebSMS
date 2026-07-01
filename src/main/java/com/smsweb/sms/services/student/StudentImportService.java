package com.smsweb.sms.services.student;

import com.smsweb.sms.dto.ImportPreviewResult;
import com.smsweb.sms.dto.StudentImportRow;
import com.smsweb.sms.models.Users.Roles;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.universal.*;
import com.smsweb.sms.repositories.admin.AcademicyearRepository;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.repositories.student.StudentRepository;
import com.smsweb.sms.repositories.universal.*;
import com.smsweb.sms.repositories.users.RoleRepository;
import com.smsweb.sms.repositories.users.UserRepository;
import com.smsweb.sms.services.users.UserService;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class StudentImportService {

    private static final Logger log = LoggerFactory.getLogger(StudentImportService.class);

    // ── Column indices (0-based) in the XLS ──────────────────────────────────
    private static final int COL_SNO        = 0;
    private static final int COL_NAME       = 1;
    private static final int COL_FATHER     = 2;
    private static final int COL_MOTHER     = 3;
    private static final int COL_SRNO       = 4;
    private static final int COL_CLASS      = 5;
    private static final int COL_SECTION    = 6;
    private static final int COL_DOB        = 7;
    private static final int COL_RELIGION   = 8;
    private static final int COL_CASTE      = 9;
    private static final int COL_CATEGORY   = 10;
    private static final int COL_GENDER     = 11;
    private static final int COL_CONTACT    = 12;
    private static final int COL_ADDRESS    = 13;
    private static final int COL_AADHAR     = 14;
    private static final int COL_BANK       = 15;
    private static final int COL_BRANCH     = 16;
    private static final int COL_ACCOUNT    = 17;
    private static final int COL_IFSC       = 18;
    // COL 19 = Customer ID (ignored)

    private static final int DATA_START_ROW = 3; // 0-based; row index 3 = 4th row

    // ── Caste deduplication corrections ──────────────────────────────────────
    private static final Map<String, String> CASTE_CORRECTIONS = Map.of(
            "TELII",   "TELI",
            "DHOBI..", "DHOBI",
            "DARJI.",  "DARJI",
            "NAAI.",   "NAAI",
            "HALWAEE", "HALWAI",
            "SONAR",   "SUNAR"
    );

    // ── Religion mapping (Excel value → DB value) ─────────────────────────────
    private static final Map<String, String> RELIGION_MAP = Map.of(
            "HINDU",  "HINDUISM",
            "MUSLIM", "ISLAM",
            "SIKH",   "SIKHISM",
            "OTHER",  "OTHER"
    );

    // ── Dummy contact counter (shared across one import session) ──────────────
    private final AtomicInteger dummyContactCounter = new AtomicInteger(0);

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final GradeRepository       gradeRepository;
    private final SectionRepository     sectionRepository;
    private final CategoryRepository    categoryRepository;
    private final CastRepository        castRepository;
    private final CityRepository        cityRepository;
    private final ProvinceRepository    provinceRepository;
    private final BankRepository        bankRepository;
    private final MediumRepository      mediumRepository;
    private final AcademicyearRepository academicYearRepository;
    private final StudentRepository     studentRepository;
    private final AcademicStudentRepository academicStudentRepository;
    private final UserRepository        userRepository;
    private final RoleRepository        roleRepository;
    private final PasswordEncoder       passwordEncoder;
    private final UserService           userService;
    private final StudentRowSaverService rowSaverService;

    public StudentImportService(GradeRepository gradeRepository,
                                SectionRepository sectionRepository,
                                CategoryRepository categoryRepository,
                                CastRepository castRepository,
                                CityRepository cityRepository,
                                ProvinceRepository provinceRepository,
                                BankRepository bankRepository,
                                MediumRepository mediumRepository,
                                AcademicyearRepository academicYearRepository,
                                StudentRepository studentRepository,
                                AcademicStudentRepository academicStudentRepository,
                                UserRepository userRepository,
                                RoleRepository roleRepository,
                                PasswordEncoder passwordEncoder,
                                UserService userService,
                                StudentRowSaverService rowSaverService) {
        this.gradeRepository        = gradeRepository;
        this.sectionRepository      = sectionRepository;
        this.categoryRepository     = categoryRepository;
        this.castRepository         = castRepository;
        this.cityRepository         = cityRepository;
        this.provinceRepository     = provinceRepository;
        this.bankRepository         = bankRepository;
        this.mediumRepository       = mediumRepository;
        this.academicYearRepository = academicYearRepository;
        this.studentRepository      = studentRepository;
        this.academicStudentRepository = academicStudentRepository;
        this.userRepository         = userRepository;
        this.roleRepository         = roleRepository;
        this.passwordEncoder        = passwordEncoder;
        this.userService            = userService;
        this.rowSaverService        = rowSaverService;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Parses the XLS byte array and validates each row against DB lookup tables.
     * Does NOT write anything to DB.
     */
    public ImportPreviewResult parseAndValidate(byte[] fileBytes) {
        log.info("Inside parseAndValidate - fileBytes.length={}", fileBytes == null ? 0 : fileBytes.length);
        initDummyCounter();
        ImportPreviewResult result = new ImportPreviewResult();

        try (InputStream is = new ByteArrayInputStream(fileBytes);
             Workbook wb = new HSSFWorkbook(is)) {

            Sheet sheet = wb.getSheetAt(0);
            int rowIdx = 0;

            for (Row row : sheet) {
                if (row.getRowNum() < DATA_START_ROW) continue;

                // Skip blank rows (check col 0 - SNo)
                String sno = cellStr(row, COL_SNO);
                if (sno.isBlank()) continue;

                StudentImportRow importRow = parseRow(row, rowIdx + 1);
                validateRow(importRow, result);
                result.getRows().add(importRow);
                rowIdx++;
            }

        } catch (Exception e) {
            log.error("Failed to parse import XLS", e);
            throw new RuntimeException("Failed to parse Excel file: " + e.getMessage(), e);
        }

        result.recalcCounts();
        return result;
    }

    /**
     * Executes the import: auto-creates missing lookup entries, then saves
     * Student + AcademicStudent for every non-ERROR row.
     * Returns an ImportPreviewResult with updated statuses (used for the result page).
     */
    @Transactional
    public ImportPreviewResult executeImport(byte[] fileBytes, School school, AcademicYear academicYear) {
        log.info("Inside executeImport - schoolId={}, academicYearId={}", school != null ? school.getId() : null, academicYear != null ? academicYear.getId() : null);
        initDummyCounter();
        ImportPreviewResult result = new ImportPreviewResult();

        // Resolve mandatory dependencies
        Medium hindi = mediumRepository.findByMediumNameIgnoreCase("HINDI")
                .orElseThrow(() -> new RuntimeException("Medium 'HINDI' not found in DB. Please create it."));

        Bank noBank = bankRepository.findByBankNameIgnoreCase("NO Bank")
                .orElseThrow(() -> new RuntimeException("Bank 'NO Bank' not found in DB."));

        // Default province for auto-created cities and students without a resolved province
        Province defaultProvince = provinceRepository.findByProvinceNameIgnoreCase("Uttar Pradesh")
                .orElseGet(() -> provinceRepository.findByProvinceNameIgnoreCase("UP")
                        .orElseThrow(() -> new RuntimeException("Province 'Uttar Pradesh' not found in DB.")));

        UserEntity loggedInUser = userService.getLoggedInUser();
        Roles studentRole = roleRepository.findByName("ROLE_STUDENT");

        try (InputStream is = new ByteArrayInputStream(fileBytes);
             Workbook wb = new HSSFWorkbook(is)) {

            Sheet sheet = wb.getSheetAt(0);
            int rowIdx = 0;

            for (Row row : sheet) {
                if (row.getRowNum() < DATA_START_ROW) continue;
                String sno = cellStr(row, COL_SNO);
                if (sno.isBlank()) continue;

                StudentImportRow importRow = parseRow(row, rowIdx + 1);

                // Resolve or auto-create Grade
                String gradeKey = importRow.getRawClass().trim();
                Grade grade = gradeRepository.findByGradeNameIgnoreCase(gradeKey).orElseGet(() -> {
                    Grade g = new Grade();
                    g.setGradeName(gradeKey.toUpperCase());
                    return gradeRepository.save(g);
                });

                // Resolve or auto-create Section
                String sectionKey = importRow.getRawSection().trim();
                Section section = sectionRepository.findBySectionNameIgnoreCase(sectionKey).orElseGet(() -> {
                    Section s = new Section();
                    s.setSectionName(sectionKey.toUpperCase());
                    return sectionRepository.save(s);
                });

                try {
                    // Delegate to a SEPARATE Spring bean so @Transactional(REQUIRES_NEW)
                    // is honoured by the proxy — each row gets its own transaction.
                    rowSaverService.save(importRow, grade, section,
                            hindi, noBank, defaultProvince,
                            school, academicYear,
                            loggedInUser, studentRole, gradeKey);
                } catch (Exception e) {
                    log.error("Failed to import row {}: {}", rowIdx + 1, e.getMessage(), e);
                    importRow.addError("Save failed: " + e.getMessage());
                }

                result.getRows().add(importRow);
                rowIdx++;
            }

        } catch (Exception e) {
            log.error("Import execution failed", e);
            throw new RuntimeException("Import failed: " + e.getMessage(), e);
        }

        result.recalcCounts();
        return result;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    /** Parse one Excel row into a StudentImportRow (no DB calls yet). */
    private StudentImportRow parseRow(Row row, int rowNum) {
        StudentImportRow r = new StudentImportRow();
        r.setRowNum(rowNum);

        // Raw values
        r.setRawSrNo(cellStr(row, COL_SRNO));
        r.setRawName(cellStr(row, COL_NAME));
        r.setRawFatherName(cellStr(row, COL_FATHER));
        r.setRawMotherName(cellStr(row, COL_MOTHER));
        r.setRawClass(cellStr(row, COL_CLASS));
        r.setRawSection(cellStr(row, COL_SECTION));
        r.setRawDob(cellStr(row, COL_DOB));
        r.setRawReligion(cellStr(row, COL_RELIGION));
        r.setRawCaste(cellStr(row, COL_CASTE));
        r.setRawCategory(cellStr(row, COL_CATEGORY));
        r.setRawGender(cellStr(row, COL_GENDER));
        r.setRawContact(cellStr(row, COL_CONTACT));
        r.setRawAddress(cellStr(row, COL_ADDRESS));
        r.setRawAadhar(cellStr(row, COL_AADHAR));
        r.setRawBankName(cellStr(row, COL_BANK));
        r.setRawBranchName(cellStr(row, COL_BRANCH));
        r.setRawAccountNo(cellStr(row, COL_ACCOUNT));
        r.setRawIfscCode(cellStr(row, COL_IFSC));

        // ── Cleaned values ────────────────────────────────────────────────────

        r.setClassSrNo(r.getRawSrNo().trim());
        // sanitizeName strips non-alpha chars (dots, numbers, Hindi glyphs) to satisfy
        // Student's @Pattern(regexp = "^[a-zA-Z\\s]*$") constraint
        r.setStudentName(sanitizeName(r.getRawName()));
        r.setFatherName(sanitizeName(r.getRawFatherName()));
        r.setMotherName(sanitizeName(r.getRawMotherName()));

        // DOB
        String dob = r.getRawDob().trim();
        if (dob.isBlank() || dob.equalsIgnoreCase("null")) {
            r.setDobStr(null);
            r.setDobIsNull(true);
        } else {
            r.setDobStr(dob);
        }

        // Religion
        String rel = r.getRawReligion().trim().toUpperCase();
        r.setReligion(RELIGION_MAP.getOrDefault(rel, rel));

        // Caste
        String caste = r.getRawCaste().trim().toUpperCase();
        r.setCasteCleaned(normaliseCaste(caste));

        // Category
        r.setCategoryName(r.getRawCategory().trim().toUpperCase());

        // Gender
        r.setGender(r.getRawGender().trim().toUpperCase());

        // Contact — pick any valid 10-digit numbers; assign dummy if none found
        String contact = r.getRawContact().trim();
        if (contact.isBlank()) {
            assignDummy(r);
        } else {
            // Split on comma or slash to handle "98765 43210 / 91234 56789" style too
            String[] parts = contact.split("[,/]", 2);
            String m1 = cleanMobileStrict(parts[0]);
            String m2 = parts.length > 1 ? cleanMobileStrict(parts[1]) : null;

            if (m1 != null) {
                r.setMobile1(m1);
                r.setMobile2(m2);        // null if second number invalid — just drop it
                if (m2 == null && parts.length > 1) {
                    r.addWarning("Mobile 2 is not 10 digits — dropped");
                }
            } else if (m2 != null) {
                // First number invalid, use second as primary
                r.setMobile1(m2);
                r.setMobile2(null);
                r.addWarning("Mobile 1 invalid — used mobile 2 as primary contact");
            } else {
                // Both invalid or non-numeric — assign dummy
                assignDummy(r);
                r.addWarning("No valid 10-digit mobile found — dummy number assigned");
            }
        }

        // Address + City (split on last comma)
        String addr = r.getRawAddress().trim();
        if (addr.contains(",")) {
            int lastComma = addr.lastIndexOf(',');
            String addrPart = addr.substring(0, lastComma).trim().toUpperCase();
            r.setAddress(addrPart.isBlank() ? "NOT PROVIDED" : addrPart);
            r.setCityName(addr.substring(lastComma + 1).trim());
        } else {
            // No comma — the whole field is treated as the city; use it as both address fallback and city
            String addrUpper = addr.toUpperCase();
            r.setAddress(addrUpper.isBlank() ? "NOT PROVIDED" : addrUpper);
            r.setCityName(addr.isBlank() ? "SITAPUR" : addr.trim());
        }

        // Bank
        r.setBankName(r.getRawBankName().trim().toUpperCase());

        // Aadhar — must be exactly 12 digits; blank it if not (update manually later)
        String rawAadhar = r.getRawAadhar().replaceAll("\\D", "").trim();
        if (rawAadhar.length() > 12) rawAadhar = rawAadhar.substring(0, 12);
        if (rawAadhar.length() == 12) {
            r.setAadharNo(rawAadhar);
        } else {
            r.setAadharNo("");   // blank — @Pattern allows empty string
            if (!rawAadhar.isBlank()) {
                r.addWarning("Aadhar '" + r.getRawAadhar().trim() + "' is not 12 digits — saved blank");
            }
        }
        r.setBranchName(r.getRawBranchName().trim().toUpperCase());
        r.setAccountNo(r.getRawAccountNo().trim());
        r.setIfscCode(r.getRawIfscCode().trim().toUpperCase());

        return r;
    }

    /**
     * Initialises the dummy contact counter from the DB.
     * Reads the highest '00000…' number already stored so the next
     * dummy is always one higher — never reuses an existing number.
     */
    private void initDummyCounter() {
        int next = 0;
        try {
            Optional<String> max = studentRepository.findMaxDummyMobile();
            if (max.isPresent() && max.get() != null) {
                next = Integer.parseInt(max.get().trim()) + 1;
            }
        } catch (Exception e) {
            log.warn("Could not read max dummy mobile from DB, starting at 0: {}", e.getMessage());
        }
        dummyContactCounter.set(next);
        log.info("Dummy contact counter initialised to {}", next);
    }

    /** Returns exactly-10-digit cleaned mobile, or null if invalid. */
    private String cleanMobileStrict(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String digits = raw.replaceAll("\\D", "").trim();
        // If longer than 10 digits, take the last 10 (strips country code like 91XXXXXXXXXX)
        if (digits.length() > 10) digits = digits.substring(digits.length() - 10);
        return digits.length() == 10 ? digits : null;
    }

    /** Assigns the next sequential dummy mobile and marks the row. */
    private void assignDummy(StudentImportRow r) {
        int idx = dummyContactCounter.getAndIncrement();
        r.setMobile1(String.format("%010d", idx));
        r.setMobile2(null);
        r.setContactDummy(true);
    }

    /** Validate a parsed row against DB lookups (read-only). Updates result summary. */
    private void validateRow(StudentImportRow r, ImportPreviewResult result) {
        boolean hasError = false;

        // ── Grade ──────────────────────────────────────────────────────────
        String gradeKey = r.getRawClass().trim();
        Grade grade = gradeRepository.findByGradeNameIgnoreCase(gradeKey).orElse(null);
        if (grade == null) {
            r.setGradeFound(false);
            result.getGradesToCreate().add(gradeKey);
            r.addWarning("Grade '" + gradeKey + "' will be auto-created");
        } else {
            r.setGradeFound(true);
            result.getGradesFound().add(gradeKey);
        }

        // ── Section ────────────────────────────────────────────────────────
        String sectionKey = r.getRawSection().trim();
        Section section = sectionRepository.findBySectionNameIgnoreCase(sectionKey).orElse(null);
        if (section == null) {
            r.setSectionFound(false);
            result.getSectionsToCreate().add(sectionKey);
            r.addWarning("Section '" + sectionKey + "' will be auto-created");
        } else {
            r.setSectionFound(true);
            result.getSectionsFound().add(sectionKey);
        }

        // ── Category ───────────────────────────────────────────────────────
        Category cat = categoryRepository.findByCategoryNameIgnoreCase(r.getCategoryName()).orElse(null);
        if (cat == null) {
            r.addError("Category '" + r.getCategoryName() + "' not found in DB");
            result.getCategoriesMissing().add(r.getCategoryName());
            hasError = true;
        } else {
            r.setCategoryFound(true);
            result.getCategoriesFound().add(r.getCategoryName());
        }

        // ── Caste ──────────────────────────────────────────────────────────
        Cast cast = castRepository.findByCastNameIgnoreCase(r.getCasteCleaned()).orElse(null);
        if (cast == null) {
            r.setCastExists(false);
            result.getCastesToCreate().add(r.getCasteCleaned());
            r.addWarning("Caste '" + r.getCasteCleaned() + "' will be auto-created");
        } else {
            r.setCastExists(true);
            result.getCastesFound().add(r.getCasteCleaned());
        }

        // ── City ───────────────────────────────────────────────────────────
        if (r.getCityName() != null && !r.getCityName().isBlank()) {
            City city = cityRepository.findByCityNameIgnoreCase(r.getCityName().trim()).orElse(null);
            if (city == null) {
                r.setCityExists(false);
                result.getCitiesToCreate().add(r.getCityName().trim());
                r.addWarning("City '" + r.getCityName() + "' will be auto-created");
            } else {
                r.setCityExists(true);
                result.getCitiesFound().add(r.getCityName().trim());
            }
        }

        // ── Bank ───────────────────────────────────────────────────────────
        if (r.getBankName().isBlank()) {
            // No bank in Excel — silently default to NO Bank, no warning needed
            r.setBankFound(true);
            result.getBanksFound().add("NO BANK");
        } else {
            Bank bank = bankRepository.findByBankNameIgnoreCase(r.getBankName()).orElse(null);
            if (bank == null) {
                r.setBankFound(false);
                result.getBanksFallback().add(r.getBankName());
                r.addWarning("Bank '" + r.getBankName() + "' not found — will use 'NO Bank'");
            } else {
                r.setBankFound(true);
                result.getBanksFound().add(r.getBankName());
            }
        }

        // ── DOB null warning ───────────────────────────────────────────────
        if (r.isDobIsNull()) {
            r.addWarning("DOB is null — will be saved as null");
        }

        // ── Dummy contact warning ──────────────────────────────────────────
        if (r.isContactDummy()) {
            r.addWarning("No contact number — dummy number assigned");
        }

        if (!hasError) {
            r.markReady();
        }
    }

    /** Strip non-alpha chars (dots, numbers, Hindi glyphs) so names pass
     *  Student's @Pattern(regexp = "^[a-zA-Z\\s]*$") constraint. */
    private String sanitizeName(String raw) {
        if (raw == null || raw.isBlank()) return "UNKNOWN";
        String clean = raw.trim().replaceAll("[^a-zA-Z\\s]", " ").replaceAll("\\s+", " ").trim().toUpperCase();
        return clean.isBlank() ? "UNKNOWN" : clean;
    }

    /** Apply caste corrections (typo dedup). */
    private String normaliseCaste(String raw) {
        if (raw == null) return "OTHER";
        String upper = raw.trim().toUpperCase();
        return CASTE_CORRECTIONS.getOrDefault(upper, upper);
    }

    /** Extract digits only, trim to 10 chars. */
    private String cleanMobile(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "").trim();
        if (digits.length() > 10) digits = digits.substring(digits.length() - 10);
        return digits.isBlank() ? null : digits;
    }

    /** Clean Aadhaar: digits only, max 12. */
    private String cleanAadhar(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "").trim();
        if (digits.length() > 12) digits = digits.substring(0, 12);
        return digits.isBlank() ? null : digits;
    }

    /** Read any cell as a trimmed String (handles numeric, string, blank). */
    private String cellStr(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    // date cell — format as dd/MMM/yyyy
                    Date d = cell.getDateCellValue();
                    yield new SimpleDateFormat("dd/MMM/yyyy", Locale.ENGLISH).format(d);
                }
                // Numeric like SR no — avoid scientific notation
                double v = cell.getNumericCellValue();
                yield (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default      -> "";
        };
    }

    /**
     * NURSERY / NURSERY (ENG) / KG → "NEW"
     * All other classes (1–12, 11 ARTS, 12 SCI, etc.) → "OLD"
     */
    private String resolveStudentType(String gradeKey) {
        if (gradeKey == null) return "OLD";
        String upper = gradeKey.trim().toUpperCase();
        return upper.startsWith("NURSERY") ? "NEW" : "OLD";
    }

    /**
     * Same password logic as StudentService:
     * last 6 of regNo + last 4 of mobile1
     */
    private String generatePassword(String regNo, String mobile) {
        String last6 = regNo.length() >= 6 ? regNo.substring(regNo.length() - 6) : regNo;
        String last4 = (mobile != null && mobile.length() >= 4)
                ? mobile.substring(mobile.length() - 4) : "0000";
        return last6 + last4;
    }
}
