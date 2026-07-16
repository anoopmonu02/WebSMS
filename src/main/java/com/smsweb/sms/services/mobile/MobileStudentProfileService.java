package com.smsweb.sms.services.mobile;

import com.smsweb.sms.dto.mobile.MobileProfileConstants;
import com.smsweb.sms.dto.mobile.MobileProfileUpdateRequest;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.mobile.BankChangeLog;
import com.smsweb.sms.models.mobile.StudentHealthInfo;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.universal.Bank;
import com.smsweb.sms.repositories.mobile.BankChangeLogRepository;
import com.smsweb.sms.repositories.student.StudentRepository;
import com.smsweb.sms.repositories.universal.BankRepository;
import com.smsweb.sms.repositories.users.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * NEW, isolated orchestration service for the mobile student profile
 * self-edit feature. Reads/writes Student and UserEntity directly via their
 * existing repositories (the same pattern several other services in this
 * codebase already use) — does NOT modify StudentService, AcademicStudentService,
 * or any shared service class. All new business rules (qualification/blood
 * group allow-lists, bank ownership validation, bank change auditing, health
 * info) live here.
 */
@Service
public class MobileStudentProfileService {

    @Autowired private StudentRepository studentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BankRepository bankRepository;
    @Autowired private BankChangeLogRepository bankChangeLogRepository;
    @Autowired private StudentHealthInfoService studentHealthInfoService;
    @Autowired private MobileImageCompressionHelper imageCompressionHelper;

    // ── Read ─────────────────────────────────────────────────────────────────

    public Map<String, Object> getEditableProfile(AcademicStudent as) {
        Student s = as.getStudent();
        Optional<StudentHealthInfo> health = studentHealthInfoService.getByAcademicStudentId(as.getId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("profilePicUrl", s.getPic() != null ? "/sms/api/v1/student/pic/" + s.getPic() : null);
        data.put("bloodGroup", s.getBloodGroup());
        data.put("fatherQualification", s.getFatherQualification());
        data.put("motherQualification", s.getMotherQualification());
        data.put("email", s.getUserEntity() != null ? s.getUserEntity().getEmail() : null);

        data.put("bankId", s.getBank() != null ? s.getBank().getId() : null);
        data.put("bankName", s.getBank() != null ? s.getBank().getBankName() : null);
        data.put("accountNo", s.getAccountNo());
        data.put("branchName", s.getBranchName());
        data.put("ifscCode", s.getIfscCode());

        data.put("height", health.map(StudentHealthInfo::getHeight).orElse(null));
        data.put("weight", health.map(StudentHealthInfo::getWeight).orElse(null));
        data.put("haveHealthIssues", health.map(StudentHealthInfo::getHaveHealthIssues).orElse(false));
        data.put("haveEyeIssue", health.map(StudentHealthInfo::getHaveEyeIssue).orElse(false));
        data.put("healthIssueDescription", health.map(StudentHealthInfo::getHealthIssueDescription).orElse(null));

        data.put("qualificationOptions", MobileProfileConstants.QUALIFICATION_OPTIONS);
        data.put("bloodGroupOptions", MobileProfileConstants.BLOOD_GROUPS);
        return data;
    }

    public java.util.List<Map<String, Object>> getBankList() {
        return bankRepository.findAll().stream()
                .map(b -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", b.getId());
                    m.put("bankName", b.getBankName());
                    return m;
                })
                .toList();
    }

    // ── Write ────────────────────────────────────────────────────────────────

    /**
     * Validates and applies the profile update. Throws IllegalArgumentException
     * with a user-facing message on any validation failure — caller (controller)
     * must translate that into a 400 response. Nothing is saved until every
     * field has passed validation.
     */
    @Transactional
    public void updateProfile(AcademicStudent as, MobileProfileUpdateRequest req) {
        Student student = as.getStudent();
        UserEntity userEntity = student.getUserEntity();

        // ── Validate everything first, save nothing until all checks pass ──

        if (req.getBloodGroup() != null && !MobileProfileConstants.isValidBloodGroup(req.getBloodGroup())) {
            throw new IllegalArgumentException("Invalid blood group selected");
        }
        if (req.getFatherQualification() != null
                && !MobileProfileConstants.isValidQualification(req.getFatherQualification())) {
            throw new IllegalArgumentException("Invalid father's qualification selected");
        }
        if (req.getMotherQualification() != null
                && !MobileProfileConstants.isValidQualification(req.getMotherQualification())) {
            throw new IllegalArgumentException("Invalid mother's qualification selected");
        }
        if (req.getEmail() != null && !MobileProfileConstants.isValidEmail(req.getEmail())) {
            throw new IllegalArgumentException("Invalid email address");
        }

        boolean bankProvided = req.getBankId() != null;
        Bank newBank = null;
        boolean isNoBankSelection = false;

        if (bankProvided) {
            newBank = bankRepository.findById(req.getBankId())
                    .orElseThrow(() -> new IllegalArgumentException("Selected bank not found"));
            isNoBankSelection = MobileProfileConstants.isNoBankPlaceholder(newBank.getBankName());

            // Real bank selected → account/branch/IFSC are mandatory. "No Bank"
            // placeholder selected → these are allowed to stay blank.
            if (!isNoBankSelection
                    && (!notBlank(req.getAccountNo()) || !notBlank(req.getBranchName()) || !notBlank(req.getIfscCode()))) {
                throw new IllegalArgumentException(
                        "Account number, branch name and IFSC code are required for the selected bank");
            }
            if (notBlank(req.getIfscCode()) && !MobileProfileConstants.isValidIfsc(req.getIfscCode())) {
                throw new IllegalArgumentException("Invalid IFSC code format");
            }
        }

        if (req.getHeight() != null && (req.getHeight() < 30 || req.getHeight() > 250)) {
            throw new IllegalArgumentException("Height must be between 30 and 250 cm");
        }
        if (req.getWeight() != null && (req.getWeight() < 5 || req.getWeight() > 200)) {
            throw new IllegalArgumentException("Weight must be between 5 and 200 kg");
        }

        // ── Bank change audit — capture OLD values before overwriting ──────
        if (bankProvided) {
            String newAccountNo  = blankToNull(req.getAccountNo());
            String newBranchName = blankToNull(req.getBranchName());
            String newIfscCode   = blankToNull(MobileProfileConstants.normalize(req.getIfscCode()));

            boolean changed = !Objects.equals(student.getBank() != null ? student.getBank().getId() : null, newBank.getId())
                    || !Objects.equals(student.getAccountNo(), newAccountNo)
                    || !Objects.equals(student.getBranchName(), newBranchName)
                    || !Objects.equals(student.getIfscCode(), newIfscCode);

            if (changed) {
                BankChangeLog logRow = new BankChangeLog();
                logRow.setAcademicStudent(as);
                logRow.setChangedBy(userEntity);
                logRow.setOldBank(student.getBank());
                logRow.setNewBank(newBank);
                logRow.setOldAccountNo(student.getAccountNo());
                logRow.setNewAccountNo(newAccountNo);
                logRow.setOldBranchName(student.getBranchName());
                logRow.setNewBranchName(newBranchName);
                logRow.setOldIfscCode(student.getIfscCode());
                logRow.setNewIfscCode(newIfscCode);
                bankChangeLogRepository.save(logRow);
            }

            student.setBank(newBank);
            student.setAccountNo(newAccountNo);
            student.setBranchName(newBranchName);
            student.setIfscCode(newIfscCode);
        }

        // ── Apply the rest ──────────────────────────────────────────────────
        if (req.getBloodGroup() != null) {
            student.setBloodGroup(MobileProfileConstants.normalize(req.getBloodGroup()));
        }
        if (req.getFatherQualification() != null) {
            student.setFatherQualification(MobileProfileConstants.normalize(req.getFatherQualification()));
        }
        if (req.getMotherQualification() != null) {
            student.setMotherQualification(MobileProfileConstants.normalize(req.getMotherQualification()));
        }
        studentRepository.save(student);

        if (req.getEmail() != null && userEntity != null) {
            userEntity.setEmail(req.getEmail().trim());
            userRepository.save(userEntity);
        }

        studentHealthInfoService.updateForStudent(
                as,
                req.getHeight(),
                req.getWeight(),
                Boolean.TRUE.equals(req.getHaveHealthIssues()),
                Boolean.TRUE.equals(req.getHaveEyeIssue()),
                req.getHealthIssueDescription(),
                userEntity);
    }

    /** Compresses + saves the uploaded photo, updates Student.pic, returns the new URL. */
    @Transactional
    public String updatePhoto(AcademicStudent as, MultipartFile file) throws IOException {
        String fileName = imageCompressionHelper.compressAndSave(file);
        Student student = as.getStudent();
        student.setPic(fileName);
        studentRepository.save(student);
        return "/sms/api/v1/student/pic/" + fileName;
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private String blankToNull(String s) {
        return notBlank(s) ? s.trim() : null;
    }
}
