package com.smsweb.sms.services.student;

import com.smsweb.sms.dto.StudentImportRow;
import com.smsweb.sms.models.Users.Roles;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.universal.*;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.repositories.student.StudentRepository;
import com.smsweb.sms.repositories.universal.*;
import com.smsweb.sms.repositories.users.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Saves one student row in its own REQUIRES_NEW transaction.
 * Must be a separate Spring bean — @Transactional(REQUIRES_NEW) only works
 * when invoked through the Spring proxy (i.e. from another bean).
 */
@Service
public class StudentRowSaverService {

    private static final Logger log = LoggerFactory.getLogger(StudentRowSaverService.class);

    private static final Map<String, String> CASTE_CORRECTIONS = Map.of(
            "TELII",   "TELI",
            "DHOBI..", "DHOBI",
            "DARJI.",  "DARJI",
            "NAAI.",   "NAAI",
            "HALWAEE", "HALWAI",
            "SONAR",   "SUNAR"
    );

    private final CastRepository             castRepository;
    private final CategoryRepository         categoryRepository;
    private final CityRepository             cityRepository;
    private final BankRepository             bankRepository;
    private final UserRepository             userRepository;
    private final StudentRepository          studentRepository;
    private final AcademicStudentRepository  academicStudentRepository;
    private final PasswordEncoder            passwordEncoder;

    public StudentRowSaverService(CastRepository castRepository,
                                  CategoryRepository categoryRepository,
                                  CityRepository cityRepository,
                                  BankRepository bankRepository,
                                  UserRepository userRepository,
                                  StudentRepository studentRepository,
                                  AcademicStudentRepository academicStudentRepository,
                                  PasswordEncoder passwordEncoder) {
        this.castRepository            = castRepository;
        this.categoryRepository        = categoryRepository;
        this.cityRepository            = cityRepository;
        this.bankRepository            = bankRepository;
        this.userRepository            = userRepository;
        this.studentRepository         = studentRepository;
        this.academicStudentRepository = academicStudentRepository;
        this.passwordEncoder           = passwordEncoder;
    }

    /**
     * Each call runs in a brand-new transaction.
     * A failure here rolls back only this one student — the caller's transaction is unaffected.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(StudentImportRow importRow,
                     Grade grade, Section section,
                     Medium hindi, Bank noBank, Province defaultProvince,
                     School school, AcademicYear academicYear,
                     UserEntity loggedInUser, Roles studentRole,
                     String gradeKey) throws Exception {
        log.info("Inside save");

        // ── Cast ──────────────────────────────────────────────────────────────
        String casteName = normaliseCaste(importRow.getCasteCleaned());
        Cast cast = castRepository.findByCastNameIgnoreCase(casteName).orElseGet(() -> {
            Cast c = new Cast();
            c.setCastName(casteName);
            return castRepository.save(c);
        });

        // ── Category ──────────────────────────────────────────────────────────
        Category category = categoryRepository
                .findByCategoryNameIgnoreCase(importRow.getCategoryName())
                .orElseThrow(() -> new RuntimeException(
                        "Category not found: " + importRow.getCategoryName()));

        // ── City (always non-null — falls back to SITAPUR) ───────────────────
        String cityName = (importRow.getCityName() != null && !importRow.getCityName().isBlank())
                ? importRow.getCityName().trim() : "SITAPUR";
        City city = cityRepository.findByCityNameIgnoreCase(cityName).orElseGet(() -> {
            City c = new City();
            c.setCityName(cityName.toUpperCase());
            c.setProvince(defaultProvince);
            return cityRepository.save(c);
        });
        Province province = (city.getProvince() != null) ? city.getProvince() : defaultProvince;

        // ── Bank ──────────────────────────────────────────────────────────────
        Bank bank = noBank;
        if (importRow.getBankName() != null && !importRow.getBankName().isBlank()) {
            bank = bankRepository.findByBankNameIgnoreCase(importRow.getBankName().trim())
                    .orElse(noBank);
        }

        // ── DOB ───────────────────────────────────────────────────────────────
        Date dob = null;
        if (importRow.getDobStr() != null) {
            try {
                dob = new SimpleDateFormat("dd/MMM/yyyy", Locale.ENGLISH)
                        .parse(importRow.getDobStr());
            } catch (Exception ignored) { /* saved as null */ }
        }

        // ── Registration number (unique per millisecond) ──────────────────────
        Thread.sleep(1);
        String regNo = "SRN-" + new SimpleDateFormat("ddMMyyyyhhmmssSSS").format(new Date());

        // ── UserEntity ────────────────────────────────────────────────────────
        String mobile1 = importRow.getMobile1();
        UserEntity userEntity = new UserEntity();
        userEntity.setUsername(regNo);
        userEntity.setPassword(passwordEncoder.encode(generatePassword(regNo, mobile1)));
        userEntity.setEmail("");
        userEntity.setEnabled(true);
        if (studentRole != null) userEntity.getRoles().add(studentRole);
        UserEntity savedUser = userRepository.save(userEntity);

        // ── Student ───────────────────────────────────────────────────────────
        Student student = new Student();
        student.setUserEntity(savedUser);
        student.setRegistrationNo(regNo);
        student.setStudentName(importRow.getStudentName());
        student.setFatherName(importRow.getFatherName());
        student.setMotherName(importRow.getMotherName());
        student.setDob(dob);
        student.setReligion(importRow.getReligion());
        student.setCast(cast);
        student.setCategory(category);
        student.setGender(importRow.getGender());
        student.setMobile1(mobile1);
        student.setMobile2(importRow.getMobile2());
        student.setAddress(importRow.getAddress());
        student.setCity(city);
        student.setProvince(province);
        student.setAadharNo(cleanAadhar(importRow.getAadharNo()));
        student.setBank(bank);
        student.setBranchName(importRow.getBranchName());
        student.setAccountNo(importRow.getAccountNo());
        student.setIfscCode(importRow.getIfscCode());
        student.setGrade(grade);
        student.setSection(section);
        student.setMedium(hindi);
        student.setSchool(school);
        student.setAcademicYear(academicYear);
        student.setStudentType(resolveStudentType(gradeKey));
        student.setSchoolStatus("OWN");
        student.setStatus("ACTIVE");
        student.setNationality("INDIAN");
        student.setCreatedBy(loggedInUser);
        Student savedStudent = studentRepository.save(student);

        // ── AcademicStudent ───────────────────────────────────────────────────
        AcademicStudent as = new AcademicStudent();
        as.setSchool(school);
        as.setAcademicYear(academicYear);
        as.setStudent(savedStudent);
        as.setGrade(grade);
        as.setSection(section);
        as.setMedium(hindi);
        as.setClassSrNo(importRow.getClassSrNo());
        as.setStatus(AcademicStudent.STATUS_ACTIVE);
        as.setDescription("Imported from legacy XLS data.");
        as.setCreatedBy(loggedInUser);
        academicStudentRepository.save(as);

        importRow.setStatus(StudentImportRow.STATUS_READY);
        importRow.getMessages().add("✅ Saved. RegNo: " + regNo);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String normaliseCaste(String raw) {
        if (raw == null) return "OTHER";
        String upper = raw.trim().toUpperCase();
        return CASTE_CORRECTIONS.getOrDefault(upper, upper);
    }

    private String resolveStudentType(String gradeKey) {
        if (gradeKey == null) return "OLD";
        String upper = gradeKey.trim().toUpperCase();
        return upper.startsWith("NURSERY") ? "NEW" : "OLD";
    }

    private String generatePassword(String regNo, String mobile) {
        String last6 = regNo.length() >= 6 ? regNo.substring(regNo.length() - 6) : regNo;
        String last4 = (mobile != null && mobile.length() >= 4)
                ? mobile.substring(mobile.length() - 4) : "0000";
        return last6 + last4;
    }

    private String cleanAadhar(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "").trim();
        if (digits.length() > 12) digits = digits.substring(0, 12);
        return digits.isBlank() ? null : digits;
    }
}
