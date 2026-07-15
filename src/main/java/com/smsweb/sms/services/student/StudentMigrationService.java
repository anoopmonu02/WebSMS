package com.smsweb.sms.services.student;

import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.MonthMapping;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.fees.FeeSubmission;
import com.smsweb.sms.models.fees.FeeSubmissionMonths;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.student.StudentDiscount;
import com.smsweb.sms.models.universal.Grade;
import com.smsweb.sms.models.universal.Medium;
import com.smsweb.sms.models.universal.MonthMaster;
import com.smsweb.sms.models.universal.Section;
import com.smsweb.sms.repositories.admin.AcademicyearRepository;
import com.smsweb.sms.repositories.admin.DiscountclassmapRepository;
import com.smsweb.sms.repositories.admin.FeeclassmapRepository;
import com.smsweb.sms.repositories.admin.MonthmappingRepository;
import com.smsweb.sms.repositories.admin.SchoolRepository;
import com.smsweb.sms.repositories.fees.FeeSubmissionRepository;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.repositories.student.StudentDiscountRepository;
import com.smsweb.sms.repositories.student.StudentRepository;
import com.smsweb.sms.repositories.universal.GradeRepository;
import com.smsweb.sms.repositories.universal.MediumRepository;
import com.smsweb.sms.repositories.universal.SectionRepository;
import com.smsweb.sms.repositories.users.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Brand-new service dedicated to the "Migrate Student" feature.
 * Deliberately does not modify StudentService, FeeSubmissionService, or any other existing
 * service - it only calls existing, already-tested public methods/repository queries
 * (StudentService.saveStudent, StudentService.getAllStudentsByGrade, and the same
 * FeeClassMap/MonthMapping/StudentDiscount repository queries FeeSubmissionService.
 * calculateFeeReminder already uses) and otherwise works with its own logic.
 *
 * Two migration paths:
 *  - Same school: insert a new AcademicStudent row for the existing Student. The old
 *    AcademicStudent row is left completely untouched (stays Active) - the old system this
 *    is replacing behaved the same way, relying on the academic-year FK to tell current from
 *    historical rather than a status flag.
 *  - Cross school: create a brand-new Student row (own login/registration no, via the normal
 *    saveStudent() creation path so it gets a fresh AcademicStudent row automatically),
 *    then deactivate the old Student + old AcademicStudent row + old login, since the
 *    student no longer belongs to that branch.
 */
@Service
public class StudentMigrationService {

    private static final Logger log = LoggerFactory.getLogger(StudentMigrationService.class);

    private final AcademicStudentRepository academicStudentRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final AcademicyearRepository academicyearRepository;
    private final MediumRepository mediumRepository;
    private final GradeRepository gradeRepository;
    private final SectionRepository sectionRepository;
    private final StudentService studentService;
    private final FeeSubmissionRepository feeSubmissionRepository;
    private final MonthmappingRepository monthmappingRepository;
    private final FeeclassmapRepository feeclassmapRepository;
    private final DiscountclassmapRepository discountclassmapRepository;
    private final StudentDiscountRepository studentDiscountRepository;

    @Autowired
    public StudentMigrationService(AcademicStudentRepository academicStudentRepository,
                                    StudentRepository studentRepository,
                                    UserRepository userRepository,
                                    SchoolRepository schoolRepository,
                                    AcademicyearRepository academicyearRepository,
                                    MediumRepository mediumRepository,
                                    GradeRepository gradeRepository,
                                    SectionRepository sectionRepository,
                                    StudentService studentService,
                                    FeeSubmissionRepository feeSubmissionRepository,
                                    MonthmappingRepository monthmappingRepository,
                                    FeeclassmapRepository feeclassmapRepository,
                                    DiscountclassmapRepository discountclassmapRepository,
                                    StudentDiscountRepository studentDiscountRepository) {
        this.academicStudentRepository = academicStudentRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.schoolRepository = schoolRepository;
        this.academicyearRepository = academicyearRepository;
        this.mediumRepository = mediumRepository;
        this.gradeRepository = gradeRepository;
        this.sectionRepository = sectionRepository;
        this.studentService = studentService;
        this.feeSubmissionRepository = feeSubmissionRepository;
        this.monthmappingRepository = monthmappingRepository;
        this.feeclassmapRepository = feeclassmapRepository;
        this.discountclassmapRepository = discountclassmapRepository;
        this.studentDiscountRepository = studentDiscountRepository;
    }

    public static class MigrationResult {
        public int sameSchoolCount = 0;
        public int crossSchoolCount = 0;
        public List<String> failures = new ArrayList<>();

        /**
         * IDs of the newly-created destination AcademicStudent rows, in the same order they
         * were migrated. Purely additive - existing callers (StudentMigrationRestController)
         * never read this field, so this doesn't change any existing behaviour. Added so
         * MidSessionMigrationService can look up the specific record it just created (to flag
         * it isMidSessionMigration=true) without duplicating the copy/deactivate logic above.
         */
        public List<Long> newAcademicStudentIds = new ArrayList<>();
    }

    /**
     * An always-empty MultipartFile, used to drive StudentService.saveStudent() down its
     * "no photo uploaded" branch (Success_no_image) so the reused creation path never touches
     * disk - the migrated student's existing pic filename is copied onto the Student object
     * directly instead.
     */
    private static class EmptyMultipartFile implements MultipartFile {
        @Override public String getName() { return "logo"; }
        @Override public String getOriginalFilename() { return ""; }
        @Override public String getContentType() { return null; }
        @Override public boolean isEmpty() { return true; }
        @Override public long getSize() { return 0; }
        @Override public byte[] getBytes() { return new byte[0]; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public void transferTo(java.io.File dest) {}
    }

    /**
     * sourceAcademicStudentIdToPendingFee: the exact AcademicStudent rows staged for
     * migration, mapped to the pending-fee amount calculated for each (may be BigDecimal.ZERO
     * if "Calculate Pending Fee" was never clicked). That amount becomes the new
     * AcademicStudent row's opening balance for the destination session.
     */
    @Transactional
    public MigrationResult migrateStudents(Map<Long, BigDecimal> sourceAcademicStudentIdToPendingFee,
                                            Long currentSchoolId,
                                            Long destSchoolId,
                                            Long destAcademicYearId,
                                            Long destMediumId,
                                            Long destGradeId,
                                            Long destSectionId,
                                            UserEntity loggedInUser) throws IOException {
        MigrationResult result = new MigrationResult();

        School destSchool = schoolRepository.findById(destSchoolId)
                .orElseThrow(() -> new IllegalArgumentException("Destination school not found"));
        AcademicYear destAcademicYear = academicyearRepository.findById(destAcademicYearId)
                .orElseThrow(() -> new IllegalArgumentException("Destination session not found"));
        Medium destMedium = mediumRepository.findById(destMediumId)
                .orElseThrow(() -> new IllegalArgumentException("Destination medium not found"));
        Grade destGrade = gradeRepository.findById(destGradeId)
                .orElseThrow(() -> new IllegalArgumentException("Destination grade not found"));
        Section destSection = sectionRepository.findById(destSectionId)
                .orElseThrow(() -> new IllegalArgumentException("Destination section not found"));

        boolean sameSchool = destSchoolId.equals(currentSchoolId);
        SimpleDateFormat sf = new SimpleDateFormat("ddMMyyyyhhmmssSSS");
        int counter = 0;

        // Duplicate guard: students who already have an active enrollment at the destination
        // school+session (from an earlier migration run, or another record in this same
        // batch) are skipped rather than migrated a second time. Built once up-front, then
        // updated as each student is migrated within this same batch.
        java.util.Set<Long> alreadyEnrolledAtDestinationStudentIds = academicStudentRepository
                .findAllBySchool_IdAndAcademicYear_IdAndStatus(destSchoolId, destAcademicYearId, "Active")
                .stream()
                .map(as -> as.getStudent() != null ? as.getStudent().getId() : null)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        for (Map.Entry<Long, BigDecimal> entry : sourceAcademicStudentIdToPendingFee.entrySet()) {
            Long academicStudentId = entry.getKey();
            BigDecimal pendingFee = entry.getValue() != null ? entry.getValue() : BigDecimal.ZERO;
            counter++;
            try {
                AcademicStudent sourceRecord = academicStudentRepository.findById(academicStudentId).orElse(null);
                if (sourceRecord == null || sourceRecord.getStudent() == null) {
                    result.failures.add("Record " + academicStudentId + ": not found");
                    continue;
                }
                Student sourceStudent = sourceRecord.getStudent();

                // Guard: this exact source record was already migrated in an earlier run
                // (cross-school migrations flip it to Inactive when done).
                if (AcademicStudent.STATUS_INACTIVE.equalsIgnoreCase(sourceRecord.getStatus())) {
                    result.failures.add(sourceStudent.getStudentName() + ": already migrated earlier (skipped duplicate)");
                    continue;
                }
                // Guard: this student already has an active record at the destination
                // school+session (covers same-school re-runs, since that path never closes
                // the old record, plus duplicate rows inside this same batch).
                if (alreadyEnrolledAtDestinationStudentIds.contains(sourceStudent.getId())) {
                    result.failures.add(sourceStudent.getStudentName() + ": already has an active record in the destination session (skipped duplicate)");
                    continue;
                }

                String sourceSessionFormat = sourceRecord.getAcademicYear() != null
                        ? sourceRecord.getAcademicYear().getSessionFormat() : "previous session";
                String openingBalanceRemark = "Pending from " + sourceSessionFormat;

                if (sameSchool) {
                    AcademicStudent newRecord = new AcademicStudent();
                    newRecord.setSchool(destSchool);
                    newRecord.setAcademicYear(destAcademicYear);
                    newRecord.setStudent(sourceStudent);
                    newRecord.setMedium(destMedium);
                    newRecord.setGrade(destGrade);
                    newRecord.setSection(destSection);
                    newRecord.setClassSrNo(sourceRecord.getClassSrNo());
                    newRecord.setBoardSrNo(sourceRecord.getBoardSrNo());
                    newRecord.setRollNo(sourceRecord.getRollNo());
                    newRecord.setDescription("Migrated to session " + destAcademicYear.getSessionFormat() + " from previous session");
                    newRecord.setOpeningBalance(pendingFee);
                    newRecord.setOpeningBalanceRemark(openingBalanceRemark);
                    newRecord.setCreatedBy(loggedInUser);
                    academicStudentRepository.save(newRecord);
                    result.newAcademicStudentIds.add(newRecord.getId());

                    // Old AcademicStudent row's status is intentionally left untouched - stays
                    // Active, exactly as instructed (matches the previous system's behaviour).
                    // isMigrated is the only field we flip here, purely so this row is excluded
                    // from future "Get Students" fetches for this source grade/section.
                    sourceRecord.setIsMigrated(true);
                    academicStudentRepository.save(sourceRecord);

                    alreadyEnrolledAtDestinationStudentIds.add(sourceStudent.getId());
                    result.sameSchoolCount++;
                } else {
                    String sourceBranchName = sourceRecord.getSchool() != null
                            ? sourceRecord.getSchool().getSchoolName() : "previous school";

                    Student newStudent = copyStudentForMigration(sourceStudent, destSchool, destAcademicYear, destMedium, destGrade, destSection);
                    String fileNameOrSchoolCode = sf.format(new Date()) + counter;
                    Student saved = studentService.saveStudent(newStudent, new EmptyMultipartFile(), fileNameOrSchoolCode, null);
                    if (saved == null) {
                        result.failures.add("Student " + sourceStudent.getStudentName() + ": could not create the new-school record");
                        continue;
                    }

                    // saveStudent() already created the initial AcademicStudent row for the new
                    // Student (with a generic "Saving at time of student creation." description
                    // and no SR numbers/opening balance) - fetch it and fill in the
                    // migration-specific fields without touching saveStudent() itself.
                    List<AcademicStudent> newRecords = academicStudentRepository.findAllByStudent_IdAndStatus(saved.getId(), "Active");
                    if (newRecords != null && !newRecords.isEmpty()) {
                        AcademicStudent newRecord = newRecords.get(0);
                        newRecord.setClassSrNo(null);
                        newRecord.setBoardSrNo(null);
                        newRecord.setRollNo(null);
                        newRecord.setDescription("This student migrated from other branch: " + sourceBranchName);
                        newRecord.setOpeningBalance(pendingFee);
                        newRecord.setOpeningBalanceRemark(openingBalanceRemark);
                        academicStudentRepository.save(newRecord);
                        result.newAcademicStudentIds.add(newRecord.getId());
                    } else {
                        log.warn("New AcademicStudent record not found right after saveStudent() for student id={}", saved.getId());
                    }

                    sourceRecord.setStatus(AcademicStudent.STATUS_INACTIVE);
                    sourceRecord.setIsMigrated(true);
                    sourceRecord.setUpdatedBy(loggedInUser);
                    academicStudentRepository.save(sourceRecord);

                    sourceStudent.setStatus(Student.STATUS_INACTIVE);
                    sourceStudent.setUpdatedBy(loggedInUser);
                    studentRepository.save(sourceStudent);

                    if (sourceStudent.getUserEntity() != null) {
                        UserEntity oldLogin = sourceStudent.getUserEntity();
                        oldLogin.setEnabled(false);
                        userRepository.save(oldLogin);
                    }

                    alreadyEnrolledAtDestinationStudentIds.add(saved.getId());
                    result.crossSchoolCount++;
                }
            } catch (Exception e) {
                log.error("Migration failed for academic-student #" + academicStudentId, e);
                result.failures.add("Record " + academicStudentId + ": " + e.getMessage());
            }
        }
        return result;
    }

    private Student copyStudentForMigration(Student source, School destSchool, AcademicYear destAcademicYear,
                                             Medium destMedium, Grade destGrade, Section destSection) {
        Student copy = new Student();

        UserEntity newUserEntity = new UserEntity();
        newUserEntity.setEmail(source.getUserEntity() != null ? source.getUserEntity().getEmail() : null);
        copy.setUserEntity(newUserEntity);

        copy.setStudentName(source.getStudentName());
        copy.setFatherName(source.getFatherName());
        copy.setMotherName(source.getMotherName());
        copy.setDob(source.getDob());
        copy.setNationality(source.getNationality());
        copy.setFatherOccupation(source.getFatherOccupation());
        copy.setMotherOccupation(source.getMotherOccupation());
        copy.setFatherQualification(source.getFatherQualification());
        copy.setMotherQualification(source.getMotherQualification());
        copy.setCategory(source.getCategory());
        copy.setCast(source.getCast());
        copy.setGender(source.getGender());
        copy.setDescription(source.getDescription());
        copy.setPic(source.getPic());
        copy.setReligion(source.getReligion());
        copy.setHeight(source.getHeight());
        copy.setWeight(source.getWeight());
        copy.setBloodGroup(source.getBloodGroup());
        copy.setBodyType(source.getBodyType());
        copy.setAddress(source.getAddress());
        copy.setLandmark(source.getLandmark());
        copy.setProvince(source.getProvince());
        copy.setCity(source.getCity());
        copy.setPincode(source.getPincode());
        copy.setMobile1(source.getMobile1());
        copy.setMobile2(source.getMobile2());
        copy.setDistanceFromSchool(source.getDistanceFromSchool());
        copy.setPreviousSchool(source.getPreviousSchool());
        copy.setPreviousClass(source.getPreviousClass());
        copy.setTcNo(source.getTcNo());
        copy.setRemovalCause(source.getRemovalCause());
        copy.setPassingYear(source.getPassingYear());
        copy.setPersonName(source.getPersonName());
        copy.setPersonContact(source.getPersonContact());
        copy.setRelationship(source.getRelationship());
        copy.setStudentType("MIGRATED");
        copy.setSchoolStatus(source.getSchoolStatus());
        copy.setBank(source.getBank());
        copy.setBranchName(source.getBranchName());
        copy.setIfscCode(source.getIfscCode());
        copy.setAccountNo(source.getAccountNo());
        copy.setAadharNo(source.getAadharNo());
        copy.setApaarId(source.getApaarId());
        copy.setPenNo(source.getPenNo());
        copy.setRemark("Migrated from " + (source.getSchool() != null ? source.getSchool().getSchoolName() : "previous school"));

        copy.setSchool(destSchool);
        copy.setAcademicYear(destAcademicYear);
        copy.setMedium(destMedium);
        copy.setGrade(destGrade);
        copy.setSection(destSection);

        return copy;
    }

    /**
     * Pending-fee calculation used only by the Migrate Student "Calculate Pending Fee" button.
     * Deliberately a brand-new method - does NOT modify or call
     * FeeSubmissionService.calculateFeeReminder, since that method is live on the Fee Reminder
     * page and must not risk any regression. Mirrors only the parts of that logic needed here:
     * carried-forward balance + fee amount for unpaid months (same Admission/Annual Fee
     * exclusion rule) - fine and any due-date fine mercy is deliberately left out entirely per
     * instruction, since migration always happens at the end of a session (no "as of date" to
     * calculate a fine against). All 12 months of the AcademicStudent's own academic year are
     * considered, not just months up to today.
     */
    public Map<Long, BigDecimal> calculatePendingFeesForMigration(List<Long> academicStudentIds) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        if (academicStudentIds == null) {
            return result;
        }
        for (Long academicStudentId : academicStudentIds) {
            BigDecimal pending = BigDecimal.ZERO;
            try {
                AcademicStudent academicStudent = academicStudentRepository.findById(academicStudentId).orElse(null);
                if (academicStudent == null || academicStudent.getStudent() == null
                        || academicStudent.getSchool() == null || academicStudent.getAcademicYear() == null
                        || academicStudent.getGrade() == null) {
                    result.put(academicStudentId, BigDecimal.ZERO);
                    continue;
                }
                Long schoolId = academicStudent.getSchool().getId();
                Long academicYearId = academicStudent.getAcademicYear().getId();
                Long gradeId = academicStudent.getGrade().getId();

                List<MonthMapping> allMonthMappings = monthmappingRepository
                        .findAllByAcademicYear_IdAndSchool_IdOrderByPriorityAsc(academicYearId, schoolId);
                List<MonthMaster> allMonths = allMonthMappings.stream()
                        .map(MonthMapping::getMonthMaster)
                        .collect(Collectors.toList());

                BigDecimal balanceAmount = BigDecimal.ZERO;
                List<MonthMaster> submittedMonths = new ArrayList<>();
                List<FeeSubmission> feeSubmissions = feeSubmissionRepository
                        .findAllByAcademicStudent_IdAndStatus(academicStudentId, "Active");
                if (feeSubmissions != null && !feeSubmissions.isEmpty()) {
                    FeeSubmission latest = feeSubmissions.stream()
                            .filter(fs -> fs.getFeeSubmissionDate() != null)
                            .max(Comparator.comparing(FeeSubmission::getFeeSubmissionDate))
                            .orElse(feeSubmissions.get(feeSubmissions.size() - 1));
                    if (latest.getFeeSubmissionBalance() != null && latest.getFeeSubmissionBalance().getBalanceAmount() != null) {
                        balanceAmount = latest.getFeeSubmissionBalance().getBalanceAmount();
                    }
                    for (FeeSubmission fs : feeSubmissions) {
                        if (fs.getFeeSubmissionMonths() == null) continue;
                        for (FeeSubmissionMonths fsm : fs.getFeeSubmissionMonths()) {
                            submittedMonths.add(fsm.getMonthMaster());
                        }
                    }
                }

                List<MonthMaster> unpaidMonths = allMonths.stream()
                        .filter(m -> !submittedMonths.contains(m))
                        .collect(Collectors.toList());

                BigDecimal feeAmount = BigDecimal.ZERO;
                if (!unpaidMonths.isEmpty()) {
                    String feeTypeToExclude = academicStudent.getStudent().getStudentType() != null
                            && academicStudent.getStudent().getStudentType().equalsIgnoreCase("Old")
                            ? "Admission Fee" : "Annual Fee";
                    List<Object[]> amtHeadList = feeclassmapRepository.findAmountAndFeeHeadNames(
                            academicYearId, schoolId,
                            unpaidMonths.stream().map(MonthMaster::getId).collect(Collectors.toList()), gradeId);
                    if (amtHeadList != null) {
                        for (Object[] row : amtHeadList) {
                            if (row != null && row.length >= 2 && row[0] instanceof BigDecimal
                                    && !feeTypeToExclude.equalsIgnoreCase(String.valueOf(row[1]))) {
                                feeAmount = feeAmount.add((BigDecimal) row[0]);
                            }
                        }
                    }

                    BigDecimal discountAmount = BigDecimal.ZERO;
                    StudentDiscount studentDiscount = studentDiscountRepository
                            .findBySchool_IdAndAcademicYear_IdAndAcademicStudent_IdAndStatus(schoolId, academicYearId, academicStudentId, "Active")
                            .orElse(null);
                    if (studentDiscount != null && studentDiscount.getDiscounthead() != null) {
                        List<Object[]> disAmtHeadList = discountclassmapRepository.findAmountAndDiscountHeadNames(
                                academicYearId, schoolId,
                                unpaidMonths.stream().map(MonthMaster::getId).collect(Collectors.toList()),
                                gradeId, studentDiscount.getDiscounthead().getId());
                        if (disAmtHeadList != null) {
                            for (Object[] row : disAmtHeadList) {
                                if (row != null && row.length >= 2 && row[0] instanceof BigDecimal
                                        && studentDiscount.getDiscounthead().getDiscountName().equalsIgnoreCase(String.valueOf(row[1]))) {
                                    discountAmount = discountAmount.add((BigDecimal) row[0]);
                                }
                            }
                        }
                    }
                    feeAmount = feeAmount.subtract(discountAmount);
                }

                pending = balanceAmount.add(feeAmount);
            } catch (Exception e) {
                log.error("Pending-fee calculation failed for academic-student #" + academicStudentId, e);
                pending = BigDecimal.ZERO;
            }
            result.put(academicStudentId, pending);
        }
        return result;
    }
}
