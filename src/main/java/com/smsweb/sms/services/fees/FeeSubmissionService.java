package com.smsweb.sms.services.fees;

import com.smsweb.sms.models.admin.*;
import com.smsweb.sms.models.fees.*;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.student.StudentDiscount;
import com.smsweb.sms.services.student.StudentService;
import com.smsweb.sms.services.mobile.PushNotificationService;
import com.smsweb.sms.models.universal.Discounthead;
import com.smsweb.sms.models.universal.Feehead;
import com.smsweb.sms.models.universal.Grade;
import com.smsweb.sms.models.universal.MonthMaster;
import com.smsweb.sms.models.admin.FeeClassMap;
import com.smsweb.sms.models.admin.SystemConfig;
import com.smsweb.sms.repositories.admin.*;
import com.smsweb.sms.repositories.fees.FeeSubmissionRepository;
import com.smsweb.sms.repositories.fees.ReceiptSequenceRepository;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.repositories.student.StudentDiscountRepository;
import com.smsweb.sms.repositories.universal.DiscountRepository;
import com.smsweb.sms.repositories.universal.FeeheadRepository;
import com.smsweb.sms.repositories.universal.GradeRepository;
import com.smsweb.sms.repositories.universal.MonthMasterRepository;
import com.smsweb.sms.services.users.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
public class FeeSubmissionService {
    private static final Logger log = LoggerFactory.getLogger(FeeSubmissionService.class);

    private final FeeSubmissionRepository feeSubmissionRepository;
    private final MonthmappingRepository monthmappingRepository;
    private final GradeRepository gradeRepository;
    private final AcademicStudentRepository academicStudentRepository;
    private final FullpaymentRepository fullpaymentRepository;
    private final FeeclassmapRepository feeclassmapRepository;
    private final DiscountclassmapRepository discountclassmapRepository;
    private final FeemonthmapRepository feemonthmapRepository;
    private final DiscountmonthmapRepository discountmonthmapRepository;
    private final AcademicyearRepository academicyearRepository;
    private final SchoolRepository schoolRepository;
    private final MonthMasterRepository monthMasterRepository;
    private final FeeheadRepository feeheadRepository;
    private final DiscountRepository discountRepository;
    private final ReceiptSequenceRepository receiptSequenceRepository;
    private final FeedateRepository feedateRepository;
    private final FineRepository fineRepository;
    private final StudentDiscountRepository studentDiscountRepository;
    private final UserService userService;
    private final SystemConfigRepository systemConfigRepository;

    @Autowired
    private StudentService studentService;

    @Autowired
    private PushNotificationService pushNotificationService; // feature: push notification on fee submission

    @Autowired
    public FeeSubmissionService(FeeSubmissionRepository feeSubmissionRepository, MonthmappingRepository monthmappingRepository, GradeRepository gradeRepository, AcademicStudentRepository academicStudentRepository,
                                FullpaymentRepository fullpaymentRepository, FeemonthmapRepository feemonthmapRepository, FeeclassmapRepository feeclassmapRepository, DiscountclassmapRepository discountclassmapRepository,
                                DiscountmonthmapRepository discountmonthmapRepository, AcademicyearRepository academicyearRepository, SchoolRepository schoolRepository, MonthMasterRepository monthMasterRepository,
                                FeeheadRepository feeheadRepository, DiscountRepository discountRepository, ReceiptSequenceRepository receiptSequenceRepository, FeedateRepository feedateRepository, FineRepository fineRepository,
                                StudentDiscountRepository studentDiscountRepository, UserService userService, SystemConfigRepository systemConfigRepository){
        this.feeSubmissionRepository = feeSubmissionRepository;
        this.monthmappingRepository = monthmappingRepository;
        this.gradeRepository = gradeRepository;
        this.academicStudentRepository = academicStudentRepository;
        this.fullpaymentRepository = fullpaymentRepository;
        this.feemonthmapRepository = feemonthmapRepository;
        this.feeclassmapRepository = feeclassmapRepository;
        this.discountclassmapRepository = discountclassmapRepository;
        this.discountmonthmapRepository = discountmonthmapRepository;
        this.academicyearRepository = academicyearRepository;
        this.schoolRepository = schoolRepository;
        this.monthMasterRepository = monthMasterRepository;
        this.feeheadRepository = feeheadRepository;
        this.discountRepository = discountRepository;
        this.receiptSequenceRepository = receiptSequenceRepository;
        this.feedateRepository = feedateRepository;
        this.fineRepository = fineRepository;
        this.studentDiscountRepository = studentDiscountRepository;
        this.userService = userService;
        this.systemConfigRepository = systemConfigRepository;
    }

    public List<FeeSubmission> getAllFeeSubmissionByAcademicYear(Long school_id, Long academic_id){
        log.info("Inside getAllFeeSubmissionByAcademicYear");
        return feeSubmissionRepository.findAllBySchool_IdAndAcademicYear_Id(school_id, academic_id);
    }

    public List<FeeSubmission> getAllFeeSubmissionForAcademicStudent(Long school_id, Long academic_id, Long academic_stu_id){
        log.info("Inside getAllFeeSubmissionForAcademicStudent");
        return feeSubmissionRepository.findAllBySchool_IdAndAcademicYear_IdAndAcademicStudent_Id(school_id, academic_id, academic_stu_id);
    }

    public List<FeeSubmission> getAllFeeSubmissionByAcademicStudent(Long academic_stu_id){
        //return feeSubmissionRepository.findAllByAcademicStudent_Id(academic_stu_id);
        return feeSubmissionRepository.findAllByAcademicStudent_IdAndStatus(academic_stu_id,"Active");
    }

    public List<FeeSubmission> getAllActiveFeeSubmissionByAcademicStudent(Long academic_stu_id){
        return feeSubmissionRepository.findAllByAcademicStudent_IdAndStatus(academic_stu_id, "Active");
    }

    /**
     * Whether the "Mid Year Migration Discount" field should be shown on Fee Submission for the
     * currently logged-in user: the system_config toggle is on AND the user is ROLE_ADMIN/
     * ROLE_SUPERADMIN. Deliberately not tied to any specific student or submission - once both
     * gates are true, the admin can apply this discount on any student's any fee submission, as
     * many times as they want (a general admin-discretion discount, not a one-time migration-
     * only field). Read-only - never mutates anything. Called once per Fee Submission page load
     * from FeeSubmissionController.getFeeSubmissionForm.
     */
    public boolean isMigrationDiscountFieldEnabledForCurrentUser(){
        log.info("Inside isMigrationDiscountFieldEnabledForCurrentUser");
        try{
            boolean configEnabled = systemConfigRepository.findByConfigName("MID_YEAR_MIGRATION_DISCOUNT_ENABLED")
                    .map(cfg -> "true".equalsIgnoreCase(cfg.getConfigValue()))
                    .orElse(false);
            if(!configEnabled) return false;

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if(auth == null) return false;
            return auth.getAuthorities().stream().anyMatch(a ->
                    a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPERADMIN"));
        }catch(Exception e){
            log.error("isMigrationDiscountFieldEnabledForCurrentUser check failed", e);
            return false;
        }
    }
    public FeeSubmission getLastFeeSubmissionOfStudentForBalance(Long school_id, Long academic_id, Long academic_stu_id){
        log.info("Inside getLastFeeSubmissionOfStudentForBalance");
        List<FeeSubmission> feeSubmissionList = feeSubmissionRepository.findTopBySchoolIdAndAcademicYearIdAndAcademicStudentIdOrderByIdDesc(school_id, academic_stu_id);
        return feeSubmissionList!=null && !feeSubmissionList.isEmpty()?feeSubmissionList.get(0):null;
    }

    public Optional<FeeSubmission> getFeeSubmissionById(Long id){
        return feeSubmissionRepository.findById(id);
    }


    // ── Mobile API ────────────────────────────────────────────────────────────

    /**
     * Returns all ACTIVE fee submissions for a student in the given academic year.
     * Used by the mobile fees screen (grid + summary + pie chart).
     *
     * @Transactional keeps the JPA session open so that lazy collections
     * (feeSubmissionMonths → monthMaster, feeSubmissionSub → feehead) can be
     * accessed by MobileFeesController.toSubmissionMap() without a
     * LazyInitializationException.  We force-initialize them here so the caller
     * gets fully-populated objects, not proxies that blow up after this method returns.
     */
    @Transactional(readOnly = true)
    public List<FeeSubmission> getActiveFeeSubmissionsForYear(Long schoolId, Long academicId, Long academicStudentId) {
        log.info("Inside getActiveFeeSubmissionsForYear");
        List<FeeSubmission> submissions = feeSubmissionRepository
                .findAllBySchoolIdAndAcademicIdAndAcademicStudentId(schoolId, academicId, academicStudentId);

        // Force-initialize lazy associations while the session is still open
        for (FeeSubmission fs : submissions) {
            if (fs.getFeeSubmissionMonths() != null) {
                fs.getFeeSubmissionMonths().forEach(m -> {
                    // Touch nested lazy association: MonthMaster
                    if (m.getMonthMaster() != null) {
                        m.getMonthMaster().getMonthName(); // triggers proxy init
                    }
                });
            }
            if (fs.getFeeSubmissionSub() != null) {
                fs.getFeeSubmissionSub().forEach(sub -> {
                    // Touch nested lazy association: Feehead
                    if (sub.getFeehead() != null) {
                        sub.getFeehead().getFeeHeadName(); // triggers proxy init
                    }
                });
            }
        }

        return submissions;
    }

    /**
     * Per-academic-year-month fee table for one student, for the mobile app's
     * Fees > Summary tab. One row per month of the academic year (calendar
     * order, via MonthMapping.priority) regardless of payment state:
     *
     *   - amount: the student's grade's expected total fee for that month —
     *     SUM(fee_class_map.amount) across every feehead marked applicable
     *     that month (fee_month_map.is_applicable), same source already used
     *     for the fee-submission "amount due" preview and receipt printouts
     *     elsewhere in this service (findFeeDetailsPerMonth).
     *   - receiptNo / submissionDate: populated only if an active fee
     *     submission for this student actually covers that month via
     *     FeeSubmissionMonths. A month is only ever added to
     *     FeeSubmissionMonths once per student in normal operation (same
     *     assumption getFeeReceiptDataForModel's submittedMonthMap already
     *     relies on), so last-write-wins here is safe.
     *   - status: "PAID" if covered by a submission; otherwise "PENDING" if
     *     that month's configured due date (fee_date.fee_submissiondate) has
     *     already passed, or "UPCOMING" if it hasn't (or no due date is
     *     configured for it yet). This distinction matters — without it,
     *     every unpaid month for the rest of the academic year gets summed
     *     into "amount due" even though most of them aren't due yet, wildly
     *     overstating what the parent actually owes right now.
     *
     * Read-only — does not create, modify, or delete any fee data.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMonthlyFeeTable(Long schoolId, Long academicYearId,
                                                          Long academicStudentId, Long gradeId) {
        log.info("Inside getMonthlyFeeTable");

        List<MonthMapping> monthMappingList = monthmappingRepository
                .findAllByAcademicYear_IdAndSchool_IdOrderByPriorityAsc(academicYearId, schoolId)
                .stream()
                .filter(mm -> mm.getMonthMaster() != null)
                .collect(Collectors.toList());

        List<Long> monthIds = monthMappingList.stream()
                .map(mm -> mm.getMonthMaster().getId())
                .collect(Collectors.toList());

        // Same old-vs-new student exclusion already applied everywhere else
        // fee_class_map is read for an individual student (processFeeData,
        // getFeeDetailsBasedOnMonth, etc.): "Admission Fee" only ever applies
        // to a genuinely new admission, "Annual Fee" only to a continuing
        // (old) one. This method previously summed every fee-head configured
        // for the grade with no such filter, so an old/re-admitted student's
        // month containing "Admission Fee" showed an inflated amount here
        // that never matched the real fee-submission calculation or receipt.
        boolean isOldStudent = true;
        AcademicStudent forStudent = academicStudentRepository.findById(academicStudentId).orElse(null);
        if (forStudent != null && forStudent.getStudent() != null) {
            Student student = forStudent.getStudent();
            int stuCounting = academicStudentRepository.countByStudent(student);
            isOldStudent = student.getStudentType() != null && student.getStudentType().equalsIgnoreCase("old")
                    || stuCounting > 1;
        }
        // Fee-medium migration: null only if forStudent itself couldn't be resolved (shouldn't
        // happen for a valid academicStudentId, but the block above already defends against it,
        // so this mirrors that same defensiveness rather than introducing a new failure mode).
        Long mediumId = (forStudent != null && forStudent.getMedium() != null)
                ? forStudent.getMedium().getId() : null;

        // Expected fee per month = SUM(fee_class_map.amount) across every
        // feehead applicable that month, for this grade — excluding whichever
        // of Admission Fee / Annual Fee doesn't apply to this student (see
        // isOldStudent above).
        Map<Long, BigDecimal> amountByMonthId = new HashMap<>();
        if (!monthIds.isEmpty()) {
            // Falls back to the grade-only overload only in the defensive null-mediumId case
            // above — normal calls always have a resolved student and go through the
            // medium-aware query.
            List<Object[]> feeRows = mediumId != null
                    ? feeclassmapRepository.findFeeDetailsPerMonth(academicYearId, schoolId, monthIds, gradeId, mediumId)
                    : feeclassmapRepository.findFeeDetailsPerMonth(academicYearId, schoolId, monthIds, gradeId);
            if (feeRows != null) {
                for (Object[] row : feeRows) {
                    String feeHeadName = row.length > 1 && row[1] != null ? row[1].toString() : null;
                    boolean excluded = feeHeadName != null &&
                            ((isOldStudent && feeHeadName.equalsIgnoreCase("Admission Fee")) ||
                                    (!isOldStudent && feeHeadName.equalsIgnoreCase("Annual Fee")));
                    if (excluded) continue;
                    BigDecimal amt = row[0] != null ? new BigDecimal(row[0].toString()) : BigDecimal.ZERO;
                    Long mId = ((Number) row[2]).longValue();
                    amountByMonthId.merge(mId, amt, BigDecimal::add);
                }
            }
        }

        // Net off the student's active discount (if any), per month — mirrors
        // the web app's own calculation (getDiscountDetailsBasedOnMonth /
        // calculateFullPaymentForDiscountedStudent), which this endpoint
        // previously never touched at all. Failure here degrades to showing
        // the gross fee rather than 500ing the whole Summary tab.
        Map<Long, BigDecimal> discountByMonthId = new HashMap<>();
        if (!monthIds.isEmpty()) {
            try {
                Optional<StudentDiscount> activeDiscount = studentDiscountRepository
                        .findBySchool_IdAndAcademicYear_IdAndAcademicStudent_IdAndStatus(
                                schoolId, academicYearId, academicStudentId, "Active");
                if (activeDiscount.isPresent() && activeDiscount.get().getDiscounthead() != null) {
                    Long discountId = activeDiscount.get().getDiscounthead().getId();
                    // Discount-medium migration: reuses the same mediumId already resolved above
                    // for the fee lookup, same fallback rule (null only in the defensive case
                    // where forStudent itself couldn't be resolved).
                    List<Object[]> discountRows = mediumId != null
                            ? discountclassmapRepository.findDiscountDetailsPerMonth(
                                    academicYearId, schoolId, monthIds, gradeId, discountId, mediumId)
                            : discountclassmapRepository.findDiscountDetailsPerMonth(
                                    academicYearId, schoolId, monthIds, gradeId, discountId);
                    if (discountRows != null) {
                        for (Object[] row : discountRows) {
                            BigDecimal amt = row[0] != null ? new BigDecimal(row[0].toString()) : BigDecimal.ZERO;
                            Long mId = ((Number) row[2]).longValue();
                            discountByMonthId.merge(mId, amt, BigDecimal::add);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("getMonthlyFeeTable: discount lookup failed for academicStudentId={} — showing gross fee",
                        academicStudentId, e);
            }
        }

        // Which months are already paid, via which receipt, and when.
        Map<Long, FeeSubmission> submissionByMonthId = new HashMap<>();
        List<FeeSubmission> submissions = getActiveFeeSubmissionsForYear(schoolId, academicYearId, academicStudentId);
        if (submissions != null) {
            for (FeeSubmission fs : submissions) {
                if (fs.getFeeSubmissionMonths() == null) continue;
                for (FeeSubmissionMonths fm : fs.getFeeSubmissionMonths()) {
                    if (fm.getMonthMaster() != null) {
                        submissionByMonthId.put(fm.getMonthMaster().getId(), fs);
                    }
                }
            }
        }

        // Configured due date per month (school/academic-year specific admin
        // setting) — used to tell "genuinely overdue" apart from "not billed
        // yet". Batched once instead of one query per month.
        Map<Long, Date> dueDateByMonthId = new HashMap<>();
        List<FeeDate> feeDates = feedateRepository.findAllByAcademicYear_IdAndSchool_IdOrderByIdDesc(academicYearId, schoolId);
        if (feeDates != null) {
            for (FeeDate fd : feeDates) {
                if (fd.getMonthMaster() != null) {
                    dueDateByMonthId.put(fd.getMonthMaster().getId(), fd.getFeeSubmissiondate());
                }
            }
        }
        Date today = new Date();

        // Some schools collect certain months' fees together (e.g. Dec+Jan
        // billed as one cycle due 1-Dec) — admin configures this via the
        // FEE_MONTH_GROUP_PAIRS system_config row (comma-separated
        // "Leader-Follower" pairs, e.g. "Dec-Jan,Feb-Mar"), optionally scoped
        // to specific schools via its school_ids column. When a leader month
        // is reached, its due date carries forward to the follower instead of
        // the follower waiting on its own (later) configured due date.
        Map<Integer, Integer> groupLeaderToFollowerMonthValue = loadMonthGroupPairs(schoolId);

        List<Map<String, Object>> table = new ArrayList<>();
        Date carriedDueDate = null;
        Integer carriedFromLeaderMonthValue = null;

        for (MonthMapping mm : monthMappingList) {
            Long mId = mm.getMonthMaster().getId();
            Month thisMonth = parseMonthName(mm.getMonthMaster().getMonthName());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("monthName", mm.getMonthMaster().getMonthName());

            BigDecimal grossAmount = amountByMonthId.getOrDefault(mId, BigDecimal.ZERO);
            BigDecimal discountAmount = discountByMonthId.getOrDefault(mId, BigDecimal.ZERO);
            BigDecimal netAmount = grossAmount.subtract(discountAmount);
            if (netAmount.compareTo(BigDecimal.ZERO) < 0) netAmount = BigDecimal.ZERO;
            row.put("amount", netAmount);
            if (discountAmount.compareTo(BigDecimal.ZERO) > 0) {
                row.put("discountAmount", discountAmount);
            }

            FeeSubmission fs = submissionByMonthId.get(mId);
            Date ownDueDate = dueDateByMonthId.get(mId);

            // Only apply the carried-forward due date if this month is
            // genuinely the configured follower of the previous leader month
            // — if month_mapping's real order doesn't match what admin typed
            // into the config, skip the override rather than mis-apply it.
            Date effectiveDueDate = ownDueDate;
            if (carriedDueDate != null && thisMonth != null && carriedFromLeaderMonthValue != null
                    && Objects.equals(groupLeaderToFollowerMonthValue.get(carriedFromLeaderMonthValue), thisMonth.getValue())) {
                effectiveDueDate = carriedDueDate;
            }
            row.put("dueDate", effectiveDueDate);

            if (fs != null) {
                row.put("receiptNo",      fs.getReceiptNo());
                row.put("submissionDate", fs.getFeeSubmissionDate());
                row.put("status",         "PAID");
            } else {
                row.put("receiptNo",      null);
                row.put("submissionDate", null);
                // No effective due date for this month yet: don't count it as
                // overdue — fall back to "UPCOMING" rather than alarming the
                // parent about something the school hasn't billed for yet.
                boolean isDue = effectiveDueDate != null && !effectiveDueDate.after(today);
                row.put("status", isDue ? "PENDING" : "UPCOMING");
            }
            table.add(row);

            // Set up the carry for the next iteration.
            if (thisMonth != null && groupLeaderToFollowerMonthValue.containsKey(thisMonth.getValue())) {
                carriedDueDate = ownDueDate;
                carriedFromLeaderMonthValue = thisMonth.getValue();
            } else {
                carriedDueDate = null;
                carriedFromLeaderMonthValue = null;
            }
        }
        return table;
    }

    /**
     * Parses the FEE_MONTH_GROUP_PAIRS system_config row (if present and
     * applicable to this school) into a leader-month-value -> follower-month-
     * value map. config_value format: comma-separated "Leader-Follower" pairs,
     * e.g. "Dec-Jan,Feb-Mar" — month names matched case-insensitively, full or
     * short form. school_ids: comma-separated school IDs this row applies to;
     * null/blank means it applies to every school. Malformed pairs are
     * skipped individually rather than failing the whole lookup.
     */
    private Map<Integer, Integer> loadMonthGroupPairs(Long schoolId) {
        Map<Integer, Integer> result = new HashMap<>();
        Optional<SystemConfig> cfgOpt = systemConfigRepository.findByConfigName("FEE_MONTH_GROUP_PAIRS");
        if (cfgOpt.isEmpty()) return result;

        SystemConfig cfg = cfgOpt.get();
        if (!appliesToSchool(cfg.getSchoolIds(), schoolId)) return result;
        if (cfg.getConfigValue() == null) return result;

        for (String pair : cfg.getConfigValue().split(",")) {
            String[] parts = pair.trim().split("-");
            if (parts.length != 2) continue;
            Month leader = parseMonthName(parts[0]);
            Month follower = parseMonthName(parts[1]);
            if (leader != null && follower != null) {
                result.put(leader.getValue(), follower.getValue());
            }
        }
        return result;
    }

    /** Blank/null schoolIds means "applies to every school". */
    private boolean appliesToSchool(String schoolIdsCsv, Long schoolId) {
        if (schoolIdsCsv == null || schoolIdsCsv.trim().isEmpty()) return true;
        for (String part : schoolIdsCsv.split(",")) {
            try {
                if (Long.parseLong(part.trim()) == schoolId) return true;
            } catch (NumberFormatException ignored) {
                // malformed entry — ignore, don't let it match everything
            }
        }
        return false;
    }

    /** Resolves a month name to java.time.Month regardless of whether it's
     *  stored/typed as a full name ("December"), a short name ("Dec"), or in
     *  any case. Returns null (never guesses) if nothing matches. */
    private Month parseMonthName(String monthName) {
        if (monthName == null) return null;
        String trimmed = monthName.trim();
        if (trimmed.isEmpty()) return null;
        try {
            return Month.valueOf(trimmed.toUpperCase());
        } catch (Exception ignored) {
            // not a full enum-constant name — fall through to short-name matching
        }
        for (Month m : Month.values()) {
            if (m.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).equalsIgnoreCase(trimmed)
                    || m.getDisplayName(TextStyle.FULL, Locale.ENGLISH).equalsIgnoreCase(trimmed)) {
                return m;
            }
        }
        return null;
    }

    /** Sorts a list of month-name strings (e.g. from a fee submission's covered
     *  months) into academic-year chronological order using month_mapping.priority,
     *  instead of whatever raw order they were originally collected in. Names that
     *  don't resolve to a known month, or aren't found in the school's month_mapping,
     *  are appended at the end in their original relative order — so a bad/unknown
     *  entry never causes the whole list to disappear. */
    @Transactional(readOnly = true)
    public List<String> sortMonthsByPriority(Long schoolId, Long academicYearId, List<String> monthNames) {
        if (monthNames == null || monthNames.size() <= 1) return monthNames;

        List<MonthMapping> monthMappingList = monthmappingRepository
                .findAllByAcademicYear_IdAndSchool_IdOrderByPriorityAsc(academicYearId, schoolId)
                .stream()
                .filter(mm -> mm.getMonthMaster() != null)
                .collect(Collectors.toList());

        Map<Integer, Integer> priorityByMonthValue = new HashMap<>();
        for (MonthMapping mm : monthMappingList) {
            Month resolved = parseMonthName(mm.getMonthMaster().getMonthName());
            if (resolved != null) {
                priorityByMonthValue.put(resolved.getValue(), mm.getPriority());
            }
        }

        List<String> sorted = new ArrayList<>(monthNames);
        sorted.sort(Comparator.comparingInt(name -> {
            Month m = parseMonthName(name);
            Integer priority = m != null ? priorityByMonthValue.get(m.getValue()) : null;
            return priority != null ? priority : Integer.MAX_VALUE;
        }));
        return sorted;
    }

    public Map getPaidMonths(Long school_id, Long academic_id, Long academic_student_id){
        log.info("Inside getPaidMonths");
        Map paidMonths = new HashMap();
        try{
            List<FeeSubmission> feeSubmissionList = feeSubmissionRepository.findAllBySchoolIdAndAcademicIdAndAcademicStudentId(school_id, academic_id, academic_student_id);
            if(feeSubmissionList!=null && !feeSubmissionList.isEmpty()){
                List<String> monthsList = new ArrayList<>();
                feeSubmissionList.forEach(feeSubmission -> {
                    feeSubmission.getFeeSubmissionMonths().forEach(months ->{
                        monthsList.add(months.getMonthMaster().getMonthName());
                    });
                });
                paidMonths.put("paidMonths", monthsList);
            }
        }catch(Exception e){
            e.printStackTrace();
            paidMonths.put("MonthError", e.getLocalizedMessage());
        }
        return paidMonths;
    }

    public Map getFeeDetailsBasedOnMonth(Long school_id, Long academic_id, Long academic_stu_id, String monthnames, Long grade_id){
        log.info("Inside getFeeDetailsBasedOnMonth");
        Map resultMap = new HashMap();
        try{
            List monNames = Arrays.stream(monthnames.split("-")).toList();
            List monIdList = new ArrayList();
            int monthCount = 0;
            Map lst = new HashMap();
            //Map feeDetails = new HashMap<>();
            SimpleDateFormat format = new SimpleDateFormat("dd-MMM-yyyy");

            for(Object monthNm: monNames){
                MonthMapping monthMapping = monthmappingRepository.findByAcademicYear_IdAndSchool_IdAndMonthMaster_MonthName(academic_id, school_id, monthNm.toString()).orElse(null);
                monIdList.add(monthMapping.getMonthMaster().getId());
                monthCount++;
            }
            Grade grade = gradeRepository.findById(grade_id).orElse(null);
            AcademicStudent academicStudent = academicStudentRepository.findById(academic_stu_id).orElse(null);
            // Fee-medium migration: every FeeClassMap lookup below now needs the student's
            // medium alongside grade — it's already loaded right here via academicStudent.
            Long mediumId = academicStudent.getMedium().getId();
            List<FeeSubmission> stuFeeSubmissionList = feeSubmissionRepository.findAllBySchoolIdAndAcademicIdAndAcademicStudentId(school_id, academic_id, academic_stu_id);

            if(stuFeeSubmissionList!=null && !stuFeeSubmissionList.isEmpty()){
                for(FeeSubmission submission: stuFeeSubmissionList){
                    monthCount += submission.getFeeSubmissionMonths().size();
                }
            }
            //Full Payment Calculated
            if(monthCount == 12){
                lst.put("lastDate", new Date());
                lst.put("amount", 0.0);
                // Full-Payment migration: medium-aware lookup, reusing the mediumId already
                // resolved above for the fee lookup (see comment there — non-null by this point).
                FullPayment fullPayment = fullpaymentRepository.findBySchool_IdAndAcademicYear_IdAndGrade_IdAndMedium_Id(school_id, academic_id, grade_id, mediumId).orElse(null);
                if(fullPayment != null){
                    if(new Date().compareTo(fullPayment.getPaymentLastDate()) <= 0){
                        StudentDiscount existingDiscount = studentDiscountRepository
                                .findBySchool_IdAndAcademicYear_IdAndAcademicStudent_IdAndStatus(
                                        school_id, academic_id, academic_stu_id, "Active")
                                .orElse(null);
                        if(existingDiscount == null){
                            // No student discount — apply configured full-payment amount
                            lst.put("lastDate", fullPayment.getPaymentLastDate());
                            lst.put("amount", fullPayment.getAmount());
                        } else {
                            // Has student discount — compute effective monthly payment via dedicated method
                            BigDecimal effectiveMonthly = calculateFullPaymentForDiscountedStudent(
                                    academic_id, school_id, grade_id, mediumId, existingDiscount);
                            if(effectiveMonthly.compareTo(BigDecimal.ZERO) > 0){
                                lst.put("lastDate", fullPayment.getPaymentLastDate());
                                lst.put("amount", effectiveMonthly);
                            }
                        }
                    }
                }
            }
            //Fees Calculated
            List<Object[]> feeData = feeclassmapRepository.findAmountAndFeeHeadNames(academic_id, school_id, monIdList, grade_id, mediumId);
            Student student = academicStudent.getStudent();
            int stuCounting = academicStudentRepository.countByStudent(student);
            List lst1 = new ArrayList<>();
            lst1 = processFeeData(student, feeData, stuCounting);
            resultMap.put("paymentlist", lst);
            resultMap.put("feelist", lst1);
        }catch(Exception e){
            e.printStackTrace();
            resultMap.put("MonthError", e.getLocalizedMessage());
        }
        return resultMap;
    }


    /**
     * Calculates the effective 1-month payment burden for a student who already holds
     * an active student discount (sibling / employee / etc.).
     *
     * Full-payment discount for such students = 1-month tuition fee  –  1-month student discount.
     *
     * The tuition fee head and the reference month used to confirm discount applicability
     * are both read from the system_config table (keys: TUITION_FEE_HEAD_ID,
     * FULL_PAYMENT_DISCOUNT_MONTH), so no fee-head names or month names are hardcoded here.
     *
     * @return effective monthly amount, or ZERO if config is missing / amounts are invalid
     */
    private BigDecimal calculateFullPaymentForDiscountedStudent(
            Long academicId, Long schoolId, Long gradeId, Long mediumId, StudentDiscount existingDiscount) {
        try {
            // ── 1. Read config keys ──────────────────────────────────────────────────
            String tuitionFeeHeadIdStr = systemConfigRepository
                    .findByConfigName("TUITION_FEE_HEAD_ID")
                    .map(SystemConfig::getConfigValue)
                    .orElseThrow(() -> new RuntimeException("system_config key TUITION_FEE_HEAD_ID not found"));

            String discountRefMonthName = systemConfigRepository
                    .findByConfigName("FULL_PAYMENT_DISCOUNT_MONTH")
                    .map(SystemConfig::getConfigValue)
                    .orElseThrow(() -> new RuntimeException("system_config key FULL_PAYMENT_DISCOUNT_MONTH not found"));

            Long tuitionFeeHeadId = Long.parseLong(tuitionFeeHeadIdStr);

            // ── 2. Get reference month master (used to verify discount is_applicable) ─
            MonthMaster refMonthMaster = monthMasterRepository.findByMonthName(discountRefMonthName);
            if(refMonthMaster == null){
                throw new RuntimeException("MonthMaster not found for config month: " + discountRefMonthName);
            }
            List<Long> refMonthIdList = Collections.singletonList(refMonthMaster.getId());

            // ── 3. Tuition fee per month ─────────────────────────────────────────────
            // Direct lookup from fee_class_map — amount is a flat per-month value per grade.
            // No month join needed; the JOIN query is only used for multi-month totals.
            BigDecimal fee1Month = feeclassmapRepository
                    .findByAcademicYear_IdAndSchool_IdAndGrade_IdAndMedium_IdAndFeehead_Id(
                            academicId, schoolId, gradeId, mediumId, tuitionFeeHeadId)
                    .map(FeeClassMap::getAmount)
                    .orElse(BigDecimal.ZERO);

            // ── 4. Student discount per month ────────────────────────────────────────
            // Use reference month to confirm is_applicable = true for this discount.
            // result[4] = SAmount = raw per-month amount from discount_class_map (not multiplied by months).
            List<Object[]> discountData = discountclassmapRepository
                    .findAmountAndDiscountHeadNames(
                            academicId, schoolId, refMonthIdList,
                            gradeId, existingDiscount.getDiscounthead().getId(), mediumId);

            BigDecimal discount1Month = BigDecimal.ZERO;
            if(discountData != null && !discountData.isEmpty()){
                discount1Month = discountData.get(0)[4] != null
                        ? (BigDecimal) discountData.get(0)[4]
                        : BigDecimal.ZERO;
            }

            // ── 5. Effective monthly payment = tuition fee – discount ────────────────
            BigDecimal effectiveMonthly = fee1Month.subtract(discount1Month);
            return effectiveMonthly.compareTo(BigDecimal.ZERO) > 0 ? effectiveMonthly : BigDecimal.ZERO;

        } catch(Exception e){
            log.error("calculateFullPaymentForDiscountedStudent failed", e);
            return BigDecimal.ZERO;
        }
    }

    public List<Map<String, Object>> processFeeData(Student student, List<Object[]> feeData, int stuCounting) {
        log.info("Inside processFeeData");
        log.debug("processFeeData - feeData size={}", feeData == null ? 0 : feeData.size());

        List<Map<String, Object>> resultList = new ArrayList<>();
        if (feeData != null && !feeData.isEmpty()) {
            boolean isOldStudent = student.getStudentType().equalsIgnoreCase("old") || stuCounting > 1;

            for (Object[] result : feeData) {
                try {
                    String feeHeadName = (String) result[1];

                    if (feeHeadName != null && !feeHeadName.trim().isEmpty() &&
                            ((isOldStudent && !feeHeadName.equalsIgnoreCase("Admission Fee")) ||
                                    (!isOldStudent && !feeHeadName.equalsIgnoreCase("Annual Fee")))) {

                        Map<String, Object> map = new HashMap<>();
                        map.put("amount", (BigDecimal) result[0]);
                        map.put("feehead", feeHeadName);
                        map.put("quantity", Integer.parseInt(result[2].toString()));
                        map.put("feeid", ((Number) result[3]).longValue());
                        resultList.add(map);
                    }
                } catch (ClassCastException | NullPointerException | NumberFormatException e) {
                    log.warn("Error processing fee data record: {}", e.getMessage());
                }
            }
        }

        return resultList;
    }

    public Map getDiscountDetailsBasedOnMonth(Long school_id, Long academic_id, Long academic_stu_id, String monthnames, Long grade_id){
        log.info("Inside getDiscountDetailsBasedOnMonth");
        Map resultMap = new HashMap();
        try{
            List monNames = Arrays.stream(monthnames.split("-")).toList();
            List monIdList = new ArrayList();
            int monthCount = 0;

            for(Object monthNm: monNames){
                MonthMapping monthMapping = monthmappingRepository.findByAcademicYear_IdAndSchool_IdAndMonthMaster_MonthName(academic_id, school_id, monthNm.toString()).orElse(null);
                monIdList.add(monthMapping.getMonthMaster().getId());
                monthCount++;
            }
            /*Grade grade = gradeRepository.findById(grade_id).orElse(null);
            AcademicStudent academicStudent = academicStudentRepository.findById(academic_stu_id).orElse(null);*/
            //Discount Calculated
            Long discountId = null;
            StudentDiscount studentDiscount = studentDiscountRepository.findBySchool_IdAndAcademicYear_IdAndAcademicStudent_IdAndStatus(school_id, academic_id, academic_stu_id, "Active").get();
            if(studentDiscount!=null){
                discountId = studentDiscount.getDiscounthead().getId();
            }

            // Discount-medium migration: resolve this student's own medium (falls back to the
            // grade-only overload below only if it can't be resolved - shouldn't happen for a
            // valid academic_stu_id, same defensive fallback used in getMonthlyFeeTable).
            AcademicStudent discountStudent = academicStudentRepository.findById(academic_stu_id).orElse(null);
            Long discountMediumId = (discountStudent != null && discountStudent.getMedium() != null)
                    ? discountStudent.getMedium().getId() : null;
            List<Object[]> discountData = discountMediumId != null
                    ? discountclassmapRepository.findAmountAndDiscountHeadNames(academic_id, school_id, monIdList, grade_id, discountId, discountMediumId)
                    : discountclassmapRepository.findAmountAndDiscountHeadNames(academic_id, school_id, monIdList, grade_id, discountId);
            if(discountData!=null && !discountData.isEmpty()){
                List<Map<String, Object>> resultList = new ArrayList<>();
                for (Object[] result : discountData) {
                    try {
                        String discountHeadName = (String) result[1];
                        Map<String, Object> map = new HashMap<>();
                        map.put("amount", (BigDecimal) result[0]);
                        map.put("discountHeadName", discountHeadName);
                        map.put("quantity", Integer.parseInt(result[2].toString()));
                        map.put("discountid", ((Number) result[3]).longValue());
                        map.put("amt", (BigDecimal) result[4]);
                        resultList.add(map);
                    } catch (ClassCastException | NullPointerException | NumberFormatException e) {
                        log.warn("Error processing discount data record: {}", e.getMessage());
                        resultMap.put("DiscountError", e.getLocalizedMessage());
                    }
                }
                resultMap.put("discountdata", resultList);
            }
        }catch(Exception e){
            e.printStackTrace();
            resultMap.put("MonthError", e.getLocalizedMessage());
        }
        return resultMap;
    }

    @Transactional
    public String generateReceiptNumber(String branchCode) {
        log.info("Inside generateReceiptNumber");
        int currentYear = Year.now().getValue(); // Get the current year

        // Fetch the sequence for the branch and current year
        ReceiptSequence sequence = receiptSequenceRepository
                .findByBranchCodeAndYear(branchCode, currentYear)
                .orElse(new ReceiptSequence(branchCode, 0, currentYear));

        // Increment the sequence value
        int nextSequence = sequence.getCurrentValue() + 1;

        // Update the currentValue in the database
        sequence.setCurrentValue(nextSequence);
        receiptSequenceRepository.save(sequence);

        // Generate and return the receipt number
        //int padding = (nextSequence < 10000) ? 4 : (nextSequence < 100000) ? 5 : 6;

        //String paddedSequence = String.format("%0" + padding + "d", nextSequence);
        //return String.format("%s/%04d/%d", branchCode, nextSequence, currentYear);
        return String.format("%s/%d/%d", branchCode, currentYear, nextSequence);
        //return String.format("%s-%d-%s", branchCode, currentYear, paddedSequence);
    }

    private String getCodeValue(String schoolName) {
        if (schoolName == null || schoolName.isEmpty()) {
            throw new IllegalArgumentException("School name cannot be null or empty");
        }

        String lowerCaseName = schoolName.toLowerCase();

        if (lowerCaseName.contains("college")) {
            return "UC";
        } else if (lowerCaseName.contains("school")) {
            return "US";
        } else if (lowerCaseName.contains("sansthan")) {
            return "DM";
        }

        return ""; // Return an empty string or throw an exception if no match is found
    }

    @Transactional
    public Map save(Map<String, String[]> paramsMap, School school, AcademicYear academicYear){
        log.info("Inside save");
        Map resultMap = new HashMap();
        try{
            if(paramsMap!=null && !paramsMap.isEmpty()) {
                // Idempotency guard: the Fee Submission form embeds a fresh token minted at
                // page-load (see FeeSubmissionController#getFeeSubmissionForm /
                // #getFeeSubmissionFormNew). If this exact token was already saved, this POST
                // is a resubmission of the same form instance - double-click, Enter-key
                // resubmit, or a browser back-and-resubmit - not a new fee submission. Return
                // the existing record instead of creating a duplicate, and do it before any of
                // the receipt-number generation / entity-building below so a rejected duplicate
                // never burns a receipt sequence number.
                String submissionToken = (paramsMap.containsKey("submissionToken") && paramsMap.get("submissionToken").length > 0)
                        ? paramsMap.get("submissionToken")[0] : null;
                if(submissionToken != null && !submissionToken.isBlank()){
                    Optional<FeeSubmission> alreadySubmitted = feeSubmissionRepository.findBySubmissionToken(submissionToken);
                    if(alreadySubmitted.isPresent()){
                        FeeSubmission existing = alreadySubmitted.get();
                        log.info("Duplicate fee submission POST detected for submissionToken={}, returning existing feeSubmissionId={}", submissionToken, existing.getId());
                        resultMap.put("student", existing.getAcademicStudent());
                        resultMap.put("Feesubmission", existing);
                        resultMap.put("feeid", existing.getId());
                        return resultMap;
                    }
                }
                SimpleDateFormat dateFormat = new SimpleDateFormat("ddMMyyyyhhmmss");
                List<String> feeSubmissionModelColumns = Arrays.asList("feesubmissiondate", "academicStudent.id", "fullPaymentAmount", "fineAmount", "fineRemark", "discountAmount", "discountHead", "totalAmount",
                        "paidAmount", "balanceAmount", "feeRemark", "headName", "months","previousBalance","paymentType", "migrationDiscountAmount");
                AcademicStudent student;// = academicStudentRepository.findById(Long.parseLong("0")).orElse(null);
                String schoolCodeVal = getCodeValue(school.getSchoolName());
                //Saving fee submission object
                Map<String, Map> feeDataMap = getColumnsValue(paramsMap, feeSubmissionModelColumns);
                if(feeDataMap!=null){
                    // canSubmitFee/findMaxSubmissionDate removed — it rejected a submission
                    // whenever its (client-captured, load-time) date wasn't strictly after the
                    // MOST RECENT fee submission across the ENTIRE database, with no school or
                    // student scoping. Under normal concurrent use, any other submission
                    // anywhere in the system landing in the gap between this form loading and
                    // the user clicking Submit would trip this and reject the whole form with a
                    // misleading "less than the last submitted date" error. No real business
                    // rule depended on this ordering, and it has no relationship to receipt
                    // numbering (that's a fully independent counter).
                    boolean proceedFlag = true;
                    Map feeMap = feeDataMap.get("FeeSubmission");
                    if(proceedFlag){
                        // Validation: if last month of session is being submitted, balance must be zero
                        Map<String, MonthMaster> feeMonMapCheck = feeDataMap.get("FeeSubmission_mon");
                        if (feeMonMapCheck != null && !feeMonMapCheck.isEmpty()) {
                            List<MonthMapping> allSessionMonths = monthmappingRepository
                                    .findAllByAcademicYear_IdAndSchool_IdOrderByPriorityAsc(academicYear.getId(), school.getId());
                            if (allSessionMonths != null && !allSessionMonths.isEmpty()) {
                                String lastMonthName = allSessionMonths.get(allSessionMonths.size() - 1).getMonthMaster().getMonthName();
                                if (feeMonMapCheck.containsKey(lastMonthName)) {
                                    BigDecimal balanceAmt = feeMap.containsKey("balanceAmount")
                                            ? new BigDecimal(feeMap.get("balanceAmount").toString())
                                            : BigDecimal.ZERO;
                                    if (balanceAmt.compareTo(BigDecimal.ZERO) > 0) {
                                        resultMap.put("fee_submission_not_allowed",
                                                "Balance amount must be zero when submitting the last month of the session. Please pay the full amount.");
                                        return resultMap;
                                    }
                                }
                            }
                        }

                        // Validation: reject if any selected month already has an Active
                        // fee-submission for this student this academic year. The Fee
                        // Submission page only disables checkboxes for months it already knew
                        // about at load time (see FeeSubmissionController#getFeeSubmissionForm's
                        // getPaidMonths-driven UI hint) - that snapshot can go stale (two tabs,
                        // two staff members, a page left open) or be bypassed entirely by a
                        // direct POST. This is the authoritative, server-side version of the
                        // same check: same underlying query as getPaidMonths() for consistency,
                        // but compared by MonthMaster id rather than name string so a renamed
                        // month is still recognized correctly. Runs before receipt-number
                        // generation / entity-building below so a rejected duplicate doesn't
                        // burn a receipt sequence number.
                        if (feeMonMapCheck != null && !feeMonMapCheck.isEmpty()
                                && feeMap != null && feeMap.containsKey("academicStudent.id")) {
                            AcademicStudent studentForMonthCheck = (AcademicStudent) feeMap.get("academicStudent.id");
                            List<FeeSubmission> existingActiveSubmissions = feeSubmissionRepository
                                    .findAllBySchoolIdAndAcademicIdAndAcademicStudentId(school.getId(), academicYear.getId(), studentForMonthCheck.getId());
                            Set<Long> alreadyPaidMonthIds = new HashSet<>();
                            if (existingActiveSubmissions != null) {
                                for (FeeSubmission existingSubmission : existingActiveSubmissions) {
                                    if (existingSubmission.getFeeSubmissionMonths() == null) continue;
                                    for (FeeSubmissionMonths existingMonth : existingSubmission.getFeeSubmissionMonths()) {
                                        if (existingMonth.getMonthMaster() != null) {
                                            alreadyPaidMonthIds.add(existingMonth.getMonthMaster().getId());
                                        }
                                    }
                                }
                            }
                            List<String> duplicateMonthNames = new ArrayList<>();
                            for (Map.Entry<String, MonthMaster> monthEntry : feeMonMapCheck.entrySet()) {
                                MonthMaster requestedMonth = monthEntry.getValue();
                                if (requestedMonth != null && alreadyPaidMonthIds.contains(requestedMonth.getId())) {
                                    duplicateMonthNames.add(monthEntry.getKey());
                                }
                            }
                            if (!duplicateMonthNames.isEmpty()) {
                                log.warn("Rejected fee submission for academicStudentId={} - months already paid: {}",
                                        studentForMonthCheck.getId(), duplicateMonthNames);
                                resultMap.put("fee_submission_not_allowed",
                                        "Fee for " + String.join(", ", duplicateMonthNames) + " has already been submitted for this student. Please refresh the page and try again.");
                                return resultMap;
                            }
                        }

                        // Validation: "Both" payment mode must carry a Cash amount and an
                        // Online amount (from the breakup popup's hidden fields, cashAmount/
                        // onlineAmount - not part of the FeeSubmission model allowlist, so read
                        // directly off paramsMap same as submissionToken/previousBalance above)
                        // that are each > 0 and sum EXACTLY to paidAmount. Server-side check on
                        // top of the popup's own client-side validation - a tampered or stale
                        // request must not be able to save a submission whose recorded Cash+
                        // Online split doesn't match what was actually collected. Runs before
                        // receipt-number generation below, same as the other early-reject
                        // validations in this method, so a rejected submission never burns a
                        // receipt sequence number.
                        String paymentTypeForValidation = feeMap != null && feeMap.containsKey("paymentType")
                                ? feeMap.get("paymentType").toString().trim() : null;
                        if ("Both".equalsIgnoreCase(paymentTypeForValidation)) {
                            BigDecimal paidAmountForValidation = feeMap.containsKey("paidAmount")
                                    ? new BigDecimal(feeMap.get("paidAmount").toString()) : BigDecimal.ZERO;
                            BigDecimal cashAmountForValidation = parseAmountParam(paramsMap, "cashAmount");
                            BigDecimal onlineAmountForValidation = parseAmountParam(paramsMap, "onlineAmount");
                            if (cashAmountForValidation == null || onlineAmountForValidation == null
                                    || cashAmountForValidation.compareTo(BigDecimal.ZERO) <= 0
                                    || onlineAmountForValidation.compareTo(BigDecimal.ZERO) <= 0) {
                                resultMap.put("fee_submission_not_allowed",
                                        "For payment type \"Both\", both Cash amount and Online amount are required and must be greater than zero.");
                                return resultMap;
                            }
                            if (cashAmountForValidation.add(onlineAmountForValidation).compareTo(paidAmountForValidation) != 0) {
                                resultMap.put("fee_submission_not_allowed",
                                        "Cash amount + Online amount must equal the total Paid Amount.");
                                return resultMap;
                            }
                        }

                        FeeSubmission feeSubmission = new FeeSubmission();
                        if(feeMap!=null){
                            if(feeMap.containsKey("academicStudent.id")){
                                student = (AcademicStudent)feeMap.get("academicStudent.id");
                                feeSubmission.setAcademicStudent(student);
                                resultMap.put("student", student);
                            }
                            feeSubmission.setAcademicYear(academicYear);
                            feeSubmission.setBalanceAmount(feeMap.containsKey("balanceAmount")?new BigDecimal(feeMap.get("balanceAmount").toString()):BigDecimal.ZERO);
                            feeSubmission.setDiscountAmount(feeMap.containsKey("discountAmount")?new BigDecimal(feeMap.get("discountAmount").toString()):BigDecimal.ZERO);
                            feeSubmission.setFineAmount(feeMap.containsKey("fineAmount")?new BigDecimal(feeMap.get("fineAmount").toString()):BigDecimal.ZERO);
                            feeSubmission.setFullPaymentAmount(feeMap.containsKey("fullPaymentAmount")?new BigDecimal(feeMap.get("fullPaymentAmount").toString()):BigDecimal.ZERO);
                            feeSubmission.setPaidAmount(feeMap.containsKey("paidAmount")?new BigDecimal(feeMap.get("paidAmount").toString()):BigDecimal.ZERO);
                            feeSubmission.setTotalAmount(feeMap.containsKey("totalAmount")?new BigDecimal(feeMap.get("totalAmount").toString()):BigDecimal.ZERO);
                            // Mid Year Migration Discount - only ever non-zero for a student
                            // migrated via Students > Mid Session Migration, on their first
                            // submission, entered by an admin. Additive only - defaults to
                            // ZERO for every other submission, exactly like before this field
                            // existed.
                            feeSubmission.setMigrationDiscountAmount(feeMap.containsKey("migrationDiscountAmount")?new BigDecimal(feeMap.get("migrationDiscountAmount").toString()):BigDecimal.ZERO);
                            if(feeMap.containsKey("discountHead")){
                                Object value = feeMap.get("discountHead");
                                if (value instanceof Discounthead) {
                                    feeSubmission.setDiscounthead((Discounthead) value);
                                } else {
                                    feeSubmission.setDiscounthead(null);
                                }
                            } else{
                                feeSubmission.setDiscounthead(null);
                            }
                            feeSubmission.setFeeRemark(feeMap.containsKey("feeRemark")?feeMap.get("feeRemark").toString().trim():null);
                            feeSubmission.setFeeSubmissionDate(feeMap.containsKey("feesubmissiondate")?(Date)feeMap.get("feesubmissiondate"):new Date());
                            feeSubmission.setFineRemark(feeMap.containsKey("fineRemark")?feeMap.get("fineRemark").toString().trim():null);
                            feeSubmission.setFullPaymentRemark("");
                            feeSubmission.setReceiptNo(generateReceiptNumber(schoolCodeVal));
                            //feeSubmission.setReceiptNo("UA/RCT/"+dateFormat.format(new Date()));
                            feeSubmission.setSchool(school);
                            feeSubmission.setStatus("Active");
                            feeSubmission.setPaymentType(feeMap.containsKey("paymentType")?feeMap.get("paymentType").toString().trim():null);
                            feeSubmission.setSubmissionToken(submissionToken);
                        }
                        List<FeeSubmissionSub> submissionSubList = new ArrayList<>();
                        Map<Feehead, BigDecimal> feeSubMap = feeDataMap.get("FeeSubmission_sub");
                        if(feeSubMap!=null){
                            for (Map.Entry<Feehead, BigDecimal> entry : feeSubMap.entrySet()){
                                FeeSubmissionSub submissionSub = new FeeSubmissionSub();
                                submissionSub.setFeehead(entry.getKey());
                                submissionSub.setAmount(entry.getValue());
                                submissionSub.setStatus("Active");
                                submissionSub.setFeeSubmission(feeSubmission);
                                submissionSubList.add(submissionSub);
                            }
                        }
                        Map<String, MonthMaster> feeMonMap = feeDataMap.get("FeeSubmission_mon");
                        List<FeeSubmissionMonths> submissionMonthsList = new ArrayList<>();
                        if(feeMonMap!=null){
                            for (Map.Entry<String, MonthMaster> entry : feeMonMap.entrySet()){
                                FeeSubmissionMonths submissionMonths = new FeeSubmissionMonths();
                                submissionMonths.setMonthMaster(entry.getValue());
                                submissionMonths.setStatus("Active");
                                submissionMonths.setFeeSubmission(feeSubmission);
                                submissionMonthsList.add(submissionMonths);
                            }
                        }
                        FeeSubmissionBalance submissionBalance = new FeeSubmissionBalance();
                        submissionBalance.setBalanceAmount(feeSubmission.getBalanceAmount());
                        submissionBalance.setFeeDate(feeSubmission.getFeeSubmissionDate());
                        submissionBalance.setStudent(feeSubmission.getAcademicStudent().getStudent());
                        submissionBalance.setStatus("Active");

                        feeSubmission.setFeeSubmissionBalance(submissionBalance);
                        submissionBalance.setFeeSubmission(feeSubmission);
                        feeSubmission.setFeeSubmissionSub(submissionSubList);
                        feeSubmission.setFeeSubmissionMonths(submissionMonthsList);
                        feeSubmission.setPaymentBreakup(buildPaymentBreakupList(feeSubmission, paramsMap));
                        feeSubmission.setCreatedBy(userService.getLoggedInUser());
                        feeSubmission.setPreviousFeeBalanceRemark(""+paramsMap.get("previousBalance")[0]);
                        try{
                            feeSubmissionRepository.save(feeSubmission);
                        } catch (DataIntegrityViolationException dive){
                            // Lost a genuine concurrent-request race against an identical
                            // submission (same submissionToken) that the pre-check above didn't
                            // catch because both requests passed it before either had committed.
                            // The unique constraint on FeeSubmission.submissionToken is the real
                            // guarantee here; the pre-check above is just the fast, common-case
                            // path that avoids burning a receipt number on the loser. Don't touch
                            // the repository again in this now-rollback-only transaction - signal
                            // the controller to look the winning row up fresh instead.
                            log.warn("Duplicate-key race on fee submission for submissionToken={}", submissionToken, dive);
                            resultMap.clear();
                            if(submissionToken != null && !submissionToken.isBlank()){
                                resultMap.put("duplicate_submission_token", submissionToken);
                            }
                            return resultMap;
                        }
                        resultMap.put("Feesubmission", feeSubmission);
                        resultMap.put("feeid", feeSubmission.getId());

                        // Push a confirmation to the parent's phone — generic by design
                        // (just the month(s) paid for), since amounts/receipts are already
                        // visible in the Fees tab once they open the app. Never allowed to
                        // affect the fee submission itself (see sendToStudents' own
                        // try/catch) — this whole block is on top of that as a second
                        // layer of safety around @Transactional here.
                        try {
                            String monthsLabel = (feeMonMap != null && !feeMonMap.isEmpty())
                                    ? String.join(", ", feeMonMap.keySet())
                                    : "";
                            String pushBody = monthsLabel.isEmpty()
                                    ? "Your fee submission has been recorded."
                                    : "Fee Submitted for month: " + monthsLabel;
                            pushNotificationService.sendToStudents(
                                    Collections.singletonList(feeSubmission.getAcademicStudent()),
                                    "Fee Submitted",
                                    pushBody,
                                    PushNotificationService.TYPE_FEE);
                        } catch (Exception pushEx) {
                            log.warn("Fee submission push notification skipped for feeSubmissionId={}", feeSubmission.getId(), pushEx);
                        }
                    }
                }
            }
        }catch(Exception e){
            e.printStackTrace();
            resultMap.put("student", null);
            resultMap.put("error", e.getLocalizedMessage());
        }
        return resultMap;
    }

    /**
     * Reads a single decimal-valued request parameter (e.g. the breakup popup's hidden
     * cashAmount/onlineAmount fields) directly off the raw paramsMap - these aren't
     * FeeSubmission model fields so they never go through getColumnsValue's allowlist.
     * Returns null (not zero) when missing/blank/unparseable, so callers can tell "not
     * provided" apart from "provided as zero".
     */
    private BigDecimal parseAmountParam(Map<String, String[]> paramsMap, String paramName) {
        if (paramsMap == null || !paramsMap.containsKey(paramName)) return null;
        String[] values = paramsMap.get(paramName);
        if (values == null || values.length == 0 || values[0] == null || values[0].isBlank()) return null;
        try {
            return new BigDecimal(values[0].trim());
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    /**
     * Builds the 1 or 2 FeeSubmissionPaymentBreakup rows for a submission, based on the
     * already-set feeSubmission.paymentType ("Cash" / "Online" / "Both"):
     *   - Cash or Online: exactly 1 row, the full paidAmount, reusing feeRemark as the
     *     description (no separate breakup remark field exists for single-mode payments).
     *   - Both: exactly 2 rows, amounts/remarks from the breakup popup's hidden fields
     *     (cashAmount/cashRemark/onlineAmount/onlineRemark) - already validated (present,
     *     >0, summing to paidAmount) by the caller before feeSubmission was built.
     *   - Anything else (null/blank/unrecognized - shouldn't happen given form validation,
     *     but must not crash an otherwise-valid submission): no rows, same as before this
     *     table existed.
     * Each row's feeSubmission back-reference is set here so cascade ALL (see
     * FeeSubmission.paymentBreakup) persists them together with the parent in one save.
     */
    private List<FeeSubmissionPaymentBreakup> buildPaymentBreakupList(FeeSubmission feeSubmission, Map<String, String[]> paramsMap) {
        List<FeeSubmissionPaymentBreakup> breakupList = new ArrayList<>();
        String paymentType = feeSubmission.getPaymentType();
        if (paymentType == null) return breakupList;

        if ("Cash".equalsIgnoreCase(paymentType) || "Online".equalsIgnoreCase(paymentType)) {
            FeeSubmissionPaymentBreakup row = new FeeSubmissionPaymentBreakup();
            row.setFeeSubmission(feeSubmission);
            row.setPaymentMode(paymentType);
            row.setAmount(feeSubmission.getPaidAmount() != null ? feeSubmission.getPaidAmount() : BigDecimal.ZERO);
            row.setDescription(feeSubmission.getFeeRemark());
            breakupList.add(row);
        } else if ("Both".equalsIgnoreCase(paymentType)) {
            BigDecimal cashAmount = parseAmountParam(paramsMap, "cashAmount");
            BigDecimal onlineAmount = parseAmountParam(paramsMap, "onlineAmount");
            String cashRemark = paramsMap.containsKey("cashRemark") ? paramsMap.get("cashRemark")[0] : null;
            String onlineRemark = paramsMap.containsKey("onlineRemark") ? paramsMap.get("onlineRemark")[0] : null;

            FeeSubmissionPaymentBreakup cashRow = new FeeSubmissionPaymentBreakup();
            cashRow.setFeeSubmission(feeSubmission);
            cashRow.setPaymentMode("Cash");
            cashRow.setAmount(cashAmount != null ? cashAmount : BigDecimal.ZERO);
            cashRow.setDescription(cashRemark);
            breakupList.add(cashRow);

            FeeSubmissionPaymentBreakup onlineRow = new FeeSubmissionPaymentBreakup();
            onlineRow.setFeeSubmission(feeSubmission);
            onlineRow.setPaymentMode("Online");
            onlineRow.setAmount(onlineAmount != null ? onlineAmount : BigDecimal.ZERO);
            onlineRow.setDescription(onlineRemark);
            breakupList.add(onlineRow);
        }
        return breakupList;
    }

    /**
     * Ready-to-print text for the receipt's "Payment:" line. "Cash ₹300.00 + Online ₹200.00"
     * when there are exactly 2 breakup rows (always Cash first, regardless of DB return order
     * - matches how the amounts were entered in the split-payment popup), otherwise just the
     * plain paymentType string ("Cash"/"Online"/"Both" if breakup rows are somehow missing) -
     * identical to what the receipt showed before this table existed.
     */
    private String buildPaymentDisplayText(FeeSubmission feeSubmission, List<Map<String, Object>> breakupList) {
        if (breakupList != null && breakupList.size() == 2) {
            BigDecimal cashAmt = null;
            BigDecimal onlineAmt = null;
            for (Map<String, Object> row : breakupList) {
                Object mode = row.get("paymentMode");
                Object amount = row.get("amount");
                BigDecimal amt = amount instanceof BigDecimal ? (BigDecimal) amount : BigDecimal.ZERO;
                if ("Cash".equalsIgnoreCase(String.valueOf(mode))) {
                    cashAmt = amt;
                } else if ("Online".equalsIgnoreCase(String.valueOf(mode))) {
                    onlineAmt = amt;
                }
            }
            if (cashAmt != null && onlineAmt != null) {
                return "Cash ₹" + cashAmt.setScale(2, java.math.RoundingMode.HALF_UP)
                        + " + Online ₹" + onlineAmt.setScale(2, java.math.RoundingMode.HALF_UP);
            }
        }
        return feeSubmission.getPaymentType();
    }

    /**
     * Fresh, standalone lookup by the Fee Submission form's idempotency token - deliberately
     * separate from save() so it always runs in its own new transaction. Used by
     * FeeSubmissionController after a duplicate-key race (see save()'s DataIntegrityViolationException
     * handling) to fetch the row that actually won, without touching the already
     * rollback-marked transaction from the failed insert attempt.
     */
    @Transactional(readOnly = true)
    public Optional<FeeSubmission> findBySubmissionToken(String submissionToken){
        return feeSubmissionRepository.findBySubmissionToken(submissionToken);
    }

    public Map<String, Map> getColumnsValue(Map<String, String[]> paramsMap, List<String> columnsList){
        log.info("Inside getColumnsValue");
        Map finalMap = new HashMap();
        Map feeMap = new HashMap();
        Map<Feehead, BigDecimal> feeSubMap = new HashMap();
        Map<String, MonthMaster> feeMonMap = new HashMap();
        try{
            SimpleDateFormat sf = new SimpleDateFormat("dd/MMM/yyyy HH:mm:ss");
            sf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
            for (Map.Entry<String, String[]> entry : paramsMap.entrySet()) {
                String key = entry.getKey();
                String[] values = entry.getValue();

                if(columnsList.contains(key)){
                    if(values.length>0 && (key.equalsIgnoreCase("headName") || key.equalsIgnoreCase("months"))){
                        for (String value : values) {
                            if(key.equalsIgnoreCase("headName")){
                                //feesubmissionsub model
                                feeSubMap.put(feeheadRepository.findById(Long.parseLong(value.split("###")[2])).get(), new BigDecimal(value.split("###")[1]));
                            } else if(key.equalsIgnoreCase("months")){
                                //feesubmissionmonth model
                                feeMonMap.put(value, monthMasterRepository.findByMonthName(value));
                            }
                        }
                    } else{
                        //feesubmission model
                        String value = values[0];
                        if(value!=null && value.trim()!=""){
                            try {
                                switch (key) {
                                    case "feesubmissiondate":
                                        feeMap.put(key, sf.parse(value));
                                        break;
                                    case "fullPaymentAmount":
                                    case "fineAmount":
                                    case "discountAmount":
                                    case "totalAmount":
                                    case "paidAmount":
                                    case "balanceAmount":
                                    case "migrationDiscountAmount":
                                        feeMap.put(key, new BigDecimal(value));
                                        break;
                                    case "discountHead":
                                        feeMap.put(key, discountRepository.findByDiscountName(value));
                                        break;
                                    case "academicStudent.id":
                                        feeMap.put(key, academicStudentRepository.findById(Long.parseLong(value)).get());
                                        break;
                                    case "fineRemark":
                                    case "previousBalance":
                                        feeMap.put("previousBalanceAmount", value);
                                        break;
                                    case "paymentType":
                                        feeMap.put("paymentType", value);
                                        break;
                                    case "feeRemark":
                                        feeMap.put(key, value);
                                        break;
                                    default:
                                        log.warn("Unknown key encountered: {}", key);
                                        break;
                                }
                            } catch (NumberFormatException e) {
                                log.warn("Invalid number format for key: {} and value: {}", key, value);
                            } catch (ParseException e) {
                                log.warn("Error parsing date for key: {} and value: {}", key, value);
                            } catch (Exception e) {
                                log.warn("Error processing key: {} with value: {} - {}", key, value, e.getMessage());
                            }
                        }
                    }
                }
            }
            finalMap.put("FeeSubmission", feeMap);
            finalMap.put("FeeSubmission_sub", feeSubMap);
            finalMap.put("FeeSubmission_mon", feeMonMap);
        }catch(Exception e){
            finalMap = null;
            throw new RuntimeException("Error: "+e.getLocalizedMessage());
        }
        return finalMap;
    }

    public Map calculateFeeReminder(Map<String, String> paramsMap, School school, AcademicYear academicYear){
        log.info("Inside calculateFeeReminder");
        Map responseMap  = new HashMap();
        try{
            Map<Long, Map> finalDataMap = new HashMap<>();
            if(paramsMap!=null && !paramsMap.isEmpty()){
                Long gradeId = Long.valueOf(paramsMap.get("grade"));
                Long secId = Long.valueOf(paramsMap.get("section"));
                Long mediumId = Long.valueOf(paramsMap.get("medium"));
                String months = paramsMap.get("checkBoxes");
                String lastDate = paramsMap.get("lastdate");
                if(paramsMap!=null && paramsMap.containsKey("month")){
                    months = paramsMap.get("month");
                }
                log.debug("calculateFeeReminder - gradeId={}, secId={}, mediumId={}, months={}, lastDate={}", gradeId, secId, mediumId, months, lastDate);
                List<Long> monIdList = new ArrayList<>();

                for(int i=0;i<months.split("-").length;i++){
                    monIdList.add(Long.valueOf(months.split("-")[i]));
                }
                // Expand to ALL months from start of academic year up to selected month (by priority)
                List<MonthMapping> allMonthsUpToSelected = monthmappingRepository.findMonthsByPriority(academicYear.getId(), school.getId(), monIdList);
                List<MonthMaster> selectedMonthsList = allMonthsUpToSelected.stream()
                        .map(MonthMapping::getMonthMaster)
                        .collect(Collectors.toList());
                Fine fine = fineRepository.findAllByAcademicYear_IdAndSchool_Id(academicYear.getId(), school.getId()).get(0);
                List<AcademicStudent> academicStudentList = academicStudentRepository.findAllBySchool_IdAndMedium_IdAndGrade_IdAndSection_IdAndAcademicYear_IdAndStatusIgnoreCase(school.getId(), mediumId, gradeId, secId, academicYear.getId(), "Active");
                //AcademicYear academicYear = academicyearRepository.findById(14L).orElse(null);
                if(academicStudentList!=null && !academicStudentList.isEmpty()){
                    log.debug("calculateFeeReminder - academicStudentList size={}", academicStudentList.size());

                    // Batch (once for the whole grade/section, not per student): which students
                    // have >=1 Active fee-submission this year, and their latest submission's
                    // balance (only present when that balance is > 0). Replaces what used to be
                    // two separate per-student queries (a COUNT check, then a single-row lookup)
                    // with two queries total for the entire list.
                    List<Long> studentIdsInList = academicStudentList.stream()
                            .map(AcademicStudent::getId).collect(Collectors.toList());
                    Set<Long> studentsWithAnySubmission = new HashSet<>(
                            feeSubmissionRepository.findAcademicStudentIdsWithAnySubmission(
                                    school.getId(), academicYear.getId(), studentIdsInList));
                    Map<Long, BigDecimal> latestBalanceById = new HashMap<>();
                    List<Object[]> balancePairs = feeSubmissionRepository
                            .getLatestBalanceAmountsForStudents(school.getId(), academicYear.getId(), studentIdsInList);
                    if (balancePairs != null) {
                        for (Object[] bp : balancePairs) {
                            Long stuId = ((Number) bp[0]).longValue();
                            BigDecimal bal = bp[1] != null ? new BigDecimal(bp[1].toString()) : BigDecimal.ZERO;
                            latestBalanceById.put(stuId, bal);
                        }
                    }

                    for(AcademicStudent academicStudent: academicStudentList){
                        Map stuMap = new HashMap<>();
                        BigDecimal balanceAmount = BigDecimal.ZERO;
                        boolean hasAnySubmission = studentsWithAnySubmission.contains(academicStudent.getId());
                        if(hasAnySubmission){
                            //Atleast 1 feesubmission happen for this student
                            //Get all submitted months
                            List<MonthMaster> submittedMonthsList = new ArrayList<>();
                            List<FeeSubmission> feeSubmissions = feeSubmissionRepository.findAllByAcademicStudent_IdAndStatus(academicStudent.getId(),"Active");
                            if(feeSubmissions!=null){
                                for(FeeSubmission submission: feeSubmissions){
                                    //Calculated All submitted months
                                    for(FeeSubmissionMonths submissionMonths : submission.getFeeSubmissionMonths()){
                                        submittedMonthsList.add(submissionMonths.getMonthMaster());
                                    }
                                }
                            }
                            // Balance comes from the batched latest-balance map above (keyed by
                            // student, computed once for the whole list via getLatestBalanceAmountsForStudents,
                            // which itself picks the highest-id/most-recent submission per student —
                            // same source of truth the Fee Submission screen's "Previous Balance"
                            // field uses). Absent from the map simply means their latest submission's
                            // balance is 0 (fully paid off), which is exactly what BigDecimal.ZERO means here.
                            balanceAmount = latestBalanceById.getOrDefault(academicStudent.getId(), BigDecimal.ZERO);
                            log.debug("submittedMonthsList size={}", submittedMonthsList == null ? 0 : submittedMonthsList.size());
                            if(submittedMonthsList!=null && !submittedMonthsList.isEmpty()){
                                if(submittedMonthsList.containsAll(selectedMonthsList)){
                                    //All selected months fee already submitted
                                    //Check only if any balance amount available
                                    stuMap.put("amount", balanceAmount);
                                    stuMap.put("fineAmount", 0);
                                    stuMap.put("monthsList", "");
                                    stuMap.put("headList", "");
                                    stuMap.put("academicStudent", toLeanAcademicStudentForFee(academicStudent));
                                    finalDataMap.put(academicStudent.getId(), stuMap);
                                } else{
                                    //Fetching months not submitted but selected
                                    List<MonthMaster> restMonthsList = selectedMonthsList.stream()
                                            .filter(monthMaster -> !submittedMonthsList.contains(monthMaster))
                                            .collect(Collectors.toList());
                                    log.debug("restMonthsList size={}", restMonthsList.size());
                                    BigDecimal amt = BigDecimal.ZERO;
                                    BigDecimal fineAmount = BigDecimal.ZERO;
                                    BigDecimal discountAmount = BigDecimal.ZERO;
                                    String headNames  = "";
                                    //Calculate Fee for rest months
                                    List<Object[]> amtHeadList = feeclassmapRepository.findAmountAndFeeHeadNames(academicYear.getId(), school.getId(), restMonthsList.stream().map(MonthMaster::getId).collect(Collectors.toList()),gradeId, mediumId);
                                    String feeTypeToexclude = academicStudent.getStudent().getStudentType().equalsIgnoreCase("Old")?"Admission Fee":"Annual Fee";
                                    if(amtHeadList!=null && !amtHeadList.isEmpty()){
                                        for(Object[] rowData : amtHeadList){
                                            if(!feeTypeToexclude.equalsIgnoreCase(rowData[1].toString())){
                                                headNames+=rowData[1]+", ";
                                                amt=amt.add((BigDecimal) rowData[0]);
                                            }
                                        }
                                    } else{
                                        responseMap.put("FEE_CLASS_MAP_NOT_FOUND","Fee-class-map not found");
                                    }
                                    log.debug("restMonths headNames={}, amt={}", headNames, amt);
                                    //Calculate Fine
                                    //int monthDiff = monthmappingService.monthDifference(14L, 4L, lastMonthName, subDate);
                                    int monthDiff = monthmappingRepository.findMonthDifference(academicYear.getId(), school.getId(), restMonthsList.get(0).getMonthName(), new SimpleDateFormat("dd/MMM/yyyy").format(new Date()));
                                    List<FeeDate> feeDates = feedateRepository.findByAcademicYearAndSchoolAndGivenMonth(academicYear.getId(), school.getId(), LocalDate.now().getMonthValue());
                                    FeeDate feeDate = null;
                                    if(feeDates!=null && !feeDates.isEmpty()){
                                        feeDate = feeDates.get(0);
                                    }
                                    int cdiff = monthmappingRepository.currentFeeDateDifference(new SimpleDateFormat("dd/MMM/yyyy").format(feeDate.getFeeSubmissiondate()), new SimpleDateFormat("dd/MMM/yyyy").format(new Date()));
                                    try {
                                        // Fine multiplier = monthDiff (past months) + 1 if current month's fee date also passed.
                                        // This mirrors calculateFine() which iterates per-month:
                                        //   past month       → always +fine
                                        //   current month    → +fine only if cdiff < 0 (due date passed)
                                        //   future month     → no fine
                                        int fineMultiplier = monthDiff + (cdiff < 0 ? 1 : 0);
                                        if (fineMultiplier >= fine.getMaxCalculated()) {
                                            fineAmount = BigDecimal.valueOf(fine.getFineAmount()).multiply(BigDecimal.valueOf(fine.getMaxCalculated()));
                                        } else {
                                            fineAmount = BigDecimal.valueOf(fine.getFineAmount()).multiply(BigDecimal.valueOf(fineMultiplier));
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        responseMap.put("error", "Error in calculating fine: "+e.getLocalizedMessage());
                                    }
                                    //Calculate Discount
                                    //BigDecimal discountAmt = BigDecimal.ZERO;
                                    StudentDiscount studentDiscount = studentDiscountRepository.findBySchool_IdAndAcademicYear_IdAndAcademicStudent_IdAndStatus(school.getId(), academicYear.getId(), academicStudent.getId(),"Active").orElse(null);
                                    if(studentDiscount!=null){
                                        List<Object[]> disAmtHeadList = discountclassmapRepository.findAmountAndDiscountHeadNames(academicYear.getId(), school.getId(), restMonthsList.stream().map(MonthMaster::getId).collect(Collectors.toList()),gradeId, studentDiscount.getDiscounthead().getId(), mediumId);
                                        if(disAmtHeadList!=null && !disAmtHeadList.isEmpty()){
                                            for(Object[] rowData : disAmtHeadList){
                                                if(studentDiscount.getDiscounthead().getDiscountName().equalsIgnoreCase(rowData[1].toString())){
                                                    discountAmount=discountAmount.add((BigDecimal) rowData[0]);
                                                }
                                            }
                                        } else{
                                            //responseMap.put("DISCOUNT_CLASS_MAP_NOT_FOUND","Discount-class-map not found");
                                            //responseMap.put("error","Discount-class-map not found");
                                        }
                                    } else{
                                        log.debug("Discount not assigned to student id={}", academicStudent.getId());
                                    }
                                    String montnNames = "";
                                    for(MonthMaster monthMaster : restMonthsList){
                                        montnNames+=monthMaster.getMonthName()+", ";
                                    }
                                    BigDecimal finalamt = balanceAmount.add(amt).add(fineAmount);
                                    finalamt = finalamt.subtract(discountAmount);
                                    stuMap.put("amount", finalamt);
                                    stuMap.put("fineAmount", fineAmount);
                                    stuMap.put("monthsList", montnNames);
                                    stuMap.put("headList", headNames);
                                    stuMap.put("academicStudent", toLeanAcademicStudentForFee(academicStudent));
                                    finalDataMap.put(academicStudent.getId(), stuMap);
                                }
                            }
                        } else{
                            //No fee submission happen till
                            log.debug("No fee submitted yet for student id={}", academicStudent.getId());
                            // Zero fee-submissions this year — fall back to the student's opening
                            // balance (dues carried in from a previous year/system via migration or
                            // Excel upload), matching the exact same rule the Fee Submission screen's
                            // "Previous Balance" field already uses. Without this, balanceAmount stays
                            // at its BigDecimal.ZERO initial value and previous-year dues silently
                            // disappear from the reminder total.
                            balanceAmount = academicStudent.getOpeningBalance() != null
                                    ? academicStudent.getOpeningBalance() : BigDecimal.ZERO;
                            BigDecimal amt = BigDecimal.ZERO;
                            BigDecimal fineAmount = BigDecimal.ZERO;
                            BigDecimal discountAmount = BigDecimal.ZERO;
                            String headNames  = "";
                            List<MonthMapping> mmList = monthmappingRepository.findMonthsByPriority(academicYear.getId(), school.getId(), monIdList);
                            if(mmList!=null && !mmList.isEmpty()){
                                List<MonthMaster> allMonthsList = mmList.stream()
                                        .map(MonthMapping::getMonthMaster)
                                        .collect(Collectors.toList());
                                if(allMonthsList!=null && !allMonthsList.isEmpty()){
                                    String feeTypeToexclude = academicStudent.getStudent().getStudentType().equalsIgnoreCase("Old")?"Admission Fee":"Annual Fee";
                                    List<Object[]> feedetails = feeclassmapRepository.findAmountAndFeeHeadNames(academicYear.getId(), school.getId(), allMonthsList.stream().map(MonthMaster::getId).collect(Collectors.toList()), gradeId, mediumId);
                                    if(feedetails!=null && !feedetails.isEmpty()){
                                        for(Object[] rowData : feedetails){
                                            if(!feeTypeToexclude.equalsIgnoreCase(rowData[1].toString())){
                                                headNames+=rowData[1]+", ";
                                                amt=amt.add((BigDecimal) rowData[0]);
                                            }
                                        }
                                    } else{
                                        log.warn("No fee class mapping found for gradeId={}", gradeId);
                                        //responseMap.put("FEE_CLASS_MAP_NOT_FOUND","Fee-class-map not found");
                                        responseMap.put("error","Fee-class-map not found");
                                    }
                                    //int monthDiff = monthmappingRepository.findMonthDifference(14L, 4L, allMonthsList.get(0).getMonthName(), new SimpleDateFormat("dd/MMM/yyyy").format(new Date()));
                                    List<FeeDate> feeDates = feedateRepository.findByAcademicYearAndSchoolAndGivenMonth(academicYear.getId(), school.getId(), LocalDate.now().getMonthValue());
                                    FeeDate feeDate = null;
                                    if(feeDates!=null && !feeDates.isEmpty()){
                                        feeDate = feeDates.get(0);
                                    }
                                    if(feeDate!=null){
                                        int cdiff = monthmappingRepository.currentFeeDateDifference(new SimpleDateFormat("dd/MMM/yyyy").format(feeDate.getFeeSubmissiondate()), new SimpleDateFormat("dd/MMM/yyyy").format(new Date()));
                                        int monthdiff = monthmappingRepository.firstMonthDifference(new SimpleDateFormat("dd/MMM/yyyy").format(new Date()), academicYear.getStartDate());
                                        try {
                                            if (monthdiff > 2) {
                                                fineAmount = BigDecimal.valueOf(fine.getFineAmount()).multiply(BigDecimal.valueOf(fine.getMaxCalculated()));
                                            } else if (monthdiff<0 && monthdiff >= -2) {
                                                if (cdiff<0) {
                                                    fineAmount = BigDecimal.valueOf(fine.getFineAmount()).multiply(BigDecimal.valueOf(monthdiff + (monthdiff * monthdiff) + 1));
                                                } else {
                                                    fineAmount = BigDecimal.valueOf(fine.getFineAmount()).multiply(BigDecimal.valueOf(monthdiff + (monthdiff * monthdiff)));//(monthdiff + (monthdiff * monthdiff)) * fine.getFineAmount();
                                                }
                                            } else if (monthdiff < -2) {
                                                fineAmount = BigDecimal.valueOf(fine.getFineAmount()).multiply(BigDecimal.valueOf(fine.getMaxCalculated()));
                                            } else {
                                                if (cdiff<0) {
                                                    fineAmount = BigDecimal.valueOf(fine.getFineAmount()).multiply(BigDecimal.valueOf(monthdiff + 1));
                                                } else {
                                                    fineAmount = BigDecimal.valueOf(fine.getFineAmount()).multiply(BigDecimal.valueOf(monthdiff));
                                                }
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                            //responseMap.put("FINE_ERROR", "Error in calculating fine: "+e.getLocalizedMessage());
                                            responseMap.put("error", "Error in calculating fine: "+e.getLocalizedMessage());
                                        }
                                    } else{
                                        responseMap.put("error","Fee date not found, Please add fee date first");
                                    }

                                    //Calculate Discount
                                    BigDecimal discountAmt = BigDecimal.ZERO;
                                    StudentDiscount studentDiscount = studentDiscountRepository.findBySchool_IdAndAcademicYear_IdAndAcademicStudent_IdAndStatus(school.getId(), academicYear.getId(), academicStudent.getId(),"Active").orElse(null);
                                    if(studentDiscount!=null){
                                        List<Object[]> disAmtHeadList = discountclassmapRepository.findAmountAndDiscountHeadNames(academicYear.getId(), school.getId(), allMonthsList.stream().map(MonthMaster::getId).collect(Collectors.toList()),gradeId, studentDiscount.getDiscounthead().getId(), mediumId);
                                        if(disAmtHeadList!=null && !disAmtHeadList.isEmpty()){
                                            for(Object[] rowData : disAmtHeadList){
                                                if(studentDiscount.getDiscounthead().getDiscountName().equalsIgnoreCase(rowData[1].toString())){
                                                    discountAmt=discountAmt.add((BigDecimal) rowData[0]);
                                                }
                                            }
                                        } else{
                                            responseMap.put("DISCOUNT_CLASS_MAP_NOT_FOUND","Discount-class-map not found");
                                        }
                                    } else{
                                        log.debug("Discount not assigned to student id={}", academicStudent.getId());
                                    }
                                    String montnNames = "";
                                    for(MonthMaster monthMaster : allMonthsList){
                                        montnNames+=monthMaster.getMonthName()+", ";
                                    }
                                    BigDecimal finalamt = balanceAmount.add(amt).add(fineAmount);
                                    finalamt = finalamt.subtract(discountAmt);
                                    stuMap.put("amount", finalamt);
                                    stuMap.put("fineAmount", fineAmount);
                                    stuMap.put("monthsList", montnNames);
                                    stuMap.put("headList", headNames);
                                    stuMap.put("academicStudent", toLeanAcademicStudentForFee(academicStudent));
                                    finalDataMap.put(academicStudent.getId(), stuMap);
                                } else{
                                    //write logs
                                }
                            } else{
                                //Write logs
                            }
                        }
                    }
                } else{
                    responseMap.put("STUDENT_NOT_FOUND","Student Not found!");
                }
            }
            // Only students who actually owe something belong in a fee REMINDER —
            // drop anyone whose computed amount is 0 (fully paid for the selected
            // months, no carried-forward balance either) or negative (overpaid/
            // credit). Filtered here, once, rather than skipping the add above, so
            // every branch's calculation logic above stays untouched either way.
            finalDataMap.entrySet().removeIf(entry -> {
                Object amt = ((Map) entry.getValue()).get("amount");
                return !(amt instanceof BigDecimal) || ((BigDecimal) amt).compareTo(BigDecimal.ZERO) <= 0;
            });
            responseMap.put("finalData", finalDataMap);
        }catch(Exception e){
            e.printStackTrace();
            responseMap.put("error", e.getLocalizedMessage());
        }
        return responseMap;
    }

    @Transactional
    public int calculateFine(List<String> selectedMonths, School school, AcademicYear academicYear, int maxFineAmount, Fine fine){
        log.info("Inside calculateFine");
        int finalFineAmount = 0;
        try{
            for(String mnName : selectedMonths){
                int monDiff = feeSubmissionRepository.getMonthDiffForFine(mnName, academicYear.getId(), school.getId());
                if(monDiff>0){
                    finalFineAmount = 0;
                } else if(monDiff==0){
                    FeeDate feedate = feedateRepository.findByAcademicYear_IdAndSchool_IdAndMonthMaster_MonthName(academicYear.getId(), school.getId(), mnName).orElse(null);
                    if(feedate!=null){
                        String formattedDate = new SimpleDateFormat("dd/MMM/yyyy").format(feedate.getFeeSubmissiondate());
                        int dateDifference = feeSubmissionRepository.getDateDifference(formattedDate);
                        if(dateDifference<0){
                            finalFineAmount+=fine.getFineAmount();
                        }
                    } else{
                        throw new RuntimeException("No Fee date found for month: "+mnName);
                    }
                } else{
                    finalFineAmount+=fine.getFineAmount();
                }
            }
            if(finalFineAmount>maxFineAmount){
                finalFineAmount = maxFineAmount;
            }
        }catch(Exception e){
            e.printStackTrace();
            finalFineAmount = -1;
            throw new RuntimeException("Error in calculating fine",e);
        }
        return finalFineAmount;
    }

    public Map<String, Object> getFeeReceiptData(Long id, School school, AcademicYear academicYear) {
        log.info("Inside getFeeReceiptData");
        Map<String, Object> modelData = new HashMap<>();
        try {
            SimpleDateFormat sf = new SimpleDateFormat("dd-MMM-yyyy");
            sf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
            FeeSubmission feeSubmission = getFeeSubmissionById(id).orElse(null);

            if (feeSubmission == null) {
                modelData.put("error", "Fee submission not found for ID: " + id);
                return modelData;
            }

            AcademicStudent academicStudent = feeSubmission.getAcademicStudent();
            if (academicStudent == null) {
                modelData.put("studentError", "Academic Student not found!");
                return modelData;
            }

            modelData.put("student", studentService.toLeanAcademicStudentMap(academicStudent));
            School feeSchool = academicStudent.getSchool();
            if (feeSchool != null) {
                Map<String, Object> schoolMap = new HashMap<>();
                schoolMap.put("id", feeSchool.getId());
                schoolMap.put("schoolName", feeSchool.getSchoolName() != null ? feeSchool.getSchoolName() : "");
                schoolMap.put("address", feeSchool.getAddress() != null ? feeSchool.getAddress() : "");
                schoolMap.put("mobile1", feeSchool.getMobile1() != null ? feeSchool.getMobile1() : "");
                schoolMap.put("mobile2", feeSchool.getMobile2() != null ? feeSchool.getMobile2() : "");
                schoolMap.put("email", feeSchool.getEmail() != null ? feeSchool.getEmail() : "");
                modelData.put("school", schoolMap);
            }
            modelData.put("academicYear", academicStudent.getAcademicYear().getSessionFormat());
            modelData.put("hasStudent", true);

            List<String> slipDateList = new ArrayList<>();
            if (feeSubmission != null) {
                Map<String, Object> fsMap = new HashMap<>();
                fsMap.put("id", feeSubmission.getId());
                fsMap.put("feeSubmissionDate", feeSubmission.getFeeSubmissionDate());
                fsMap.put("receiptNo", feeSubmission.getReceiptNo() != null ? feeSubmission.getReceiptNo() : "");
                fsMap.put("paymentType", feeSubmission.getPaymentType() != null ? feeSubmission.getPaymentType() : "");
                fsMap.put("fineAmount", feeSubmission.getFineAmount());
                fsMap.put("fineRemark", feeSubmission.getFineRemark() != null ? feeSubmission.getFineRemark() : "");
                fsMap.put("discountAmount", feeSubmission.getDiscountAmount());
                fsMap.put("totalAmount", feeSubmission.getTotalAmount());
                fsMap.put("paidAmount", feeSubmission.getPaidAmount());
                fsMap.put("balanceAmount", feeSubmission.getBalanceAmount());
                fsMap.put("previousFeeBalanceRemark", feeSubmission.getPreviousFeeBalanceRemark() != null ? feeSubmission.getPreviousFeeBalanceRemark() : "");
                fsMap.put("migrationDiscountAmount", feeSubmission.getMigrationDiscountAmount() != null ? feeSubmission.getMigrationDiscountAmount() : BigDecimal.ZERO);
                fsMap.put("status", feeSubmission.getStatus() != null ? feeSubmission.getStatus() : "");
                // Cash+Online breakup, for the "Payment:" line on the receipt. paymentDisplay
                // is the ready-to-print text: "Cash ₹300.00 + Online ₹200.00" when exactly 2
                // breakup rows exist (a "Both" submission), otherwise just the plain
                // paymentType ("Cash"/"Online") - same as before this table existed. That
                // covers historical submissions saved before this table existed too (no
                // breakup rows for those). paymentBreakup is the raw list alongside it, for
                // any future consumer that needs the structured amounts rather than the
                // pre-formatted string.
                List<Map<String, Object>> breakupList = new ArrayList<>();
                if (feeSubmission.getPaymentBreakup() != null) {
                    for (FeeSubmissionPaymentBreakup breakup : feeSubmission.getPaymentBreakup()) {
                        Map<String, Object> breakupRow = new HashMap<>();
                        breakupRow.put("paymentMode", breakup.getPaymentMode());
                        breakupRow.put("amount", breakup.getAmount());
                        breakupList.add(breakupRow);
                    }
                }
                fsMap.put("paymentBreakup", breakupList);
                fsMap.put("paymentDisplay", buildPaymentDisplayText(feeSubmission, breakupList));
                if (feeSubmission.getDiscounthead() != null) {
                    fsMap.put("discounthead", Map.of("discountName", feeSubmission.getDiscounthead().getDiscountName() != null ? feeSubmission.getDiscounthead().getDiscountName() : ""));
                }
                modelData.put("feeSubmission", fsMap);
                modelData.put("hasFeeSubmission", true);

                HashMap<MonthMaster, Date> submittedMonthMap = new LinkedHashMap<>();
                List<MonthMapping> monthMappingList = monthmappingRepository.findAllByAcademicYear_IdAndSchool_IdOrderByPriorityAsc(academicYear.getId(), school.getId());
                List<FeeSubmission> feeSubmissionList = getAllActiveFeeSubmissionByAcademicStudent(academicStudent.getId());

                if (feeSubmissionList != null) {
                    for (FeeSubmission submission : feeSubmissionList) {
                        for (FeeSubmissionMonths feeMonths : submission.getFeeSubmissionMonths()) {
                            submittedMonthMap.put(feeMonths.getMonthMaster(), submission.getFeeSubmissionDate());
                        }
                    }
                }

                int i = 1;
                for (MonthMapping mm : monthMappingList) {
                    String dateString = "Month-" + i + " ####(" + mm.getMonthMaster().getMonthName().toUpperCase() + "): ####";
                    if (submittedMonthMap.containsKey(mm.getMonthMaster())) {
                        dateString += "PAID " + sf.format(submittedMonthMap.get(mm.getMonthMaster()));
                    }
                    slipDateList.add(dateString);
                    i++;
                }
                modelData.put("feeSubmittedMonths", slipDateList);
                modelData.put("feesublist", buildFeeHeadBreakdownForReceipt(feeSubmission, academicStudent, school, academicYear));
            } else {
                modelData.put("feeSubmissionError", "Fee not found for: " + academicStudent.getStudent().getStudentName() + "!");
            }
        } catch (Exception e) {
            modelData.put("error", e.getLocalizedMessage());
            e.printStackTrace();
        }
        log.debug("getFeeReceiptDataForModel result keys={}", modelData.keySet());
        return modelData;
    }

    /**
     * Builds the fee-head breakdown shown on the receipt, adding a (rate x qty) hint next to
     * each head's stored total when we can safely re-derive the quantity.
     *
     * No new persistence: the stored {@code FeeSubmissionSub.amount} always remains the
     * authoritative total. Quantity is recomputed from this submission's own
     * {@code FeeSubmissionMonths} (already persisted per-submission) joined against the
     * existing fee_class_map/fee_month_map config via {@code findAmountAndFeeHeadNames}
     * (its own SUM(amount) column is ignored - only the per-head month COUNT is used).
     * rate = amount / quantity, rounded HALF_UP to 2 decimals.
     *
     * If a head has no matching quantity (config changed since, head not in fee_class_map
     * for this grade/months, quantity 0, etc.) we simply fall back to showing the plain
     * stored amount - same as before this feature existed.
     */
    private List<Map<String, Object>> buildFeeHeadBreakdownForReceipt(FeeSubmission feeSubmission, AcademicStudent academicStudent, School school, AcademicYear academicYear) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<Long, Integer> quantityByFeeheadId = new HashMap<>();
        try {
            Grade grade = academicStudent.getGrade();
            List<Long> monthIds = feeSubmission.getFeeSubmissionMonths().stream()
                    .map(fsm -> fsm.getMonthMaster().getId())
                    .distinct()
                    .collect(Collectors.toList());

            if (grade != null && !monthIds.isEmpty()) {
                List<Object[]> feeData = feeclassmapRepository.findAmountAndFeeHeadNames(
                        academicYear.getId(), school.getId(), monthIds, grade.getId(), academicStudent.getMedium().getId());
                for (Object[] row : feeData) {
                    try {
                        Long feeheadId = ((Number) row[3]).longValue();
                        int qty = Integer.parseInt(row[2].toString());
                        quantityByFeeheadId.put(feeheadId, qty);
                    } catch (Exception rowEx) {
                        log.warn("buildFeeHeadBreakdownForReceipt - skipping unreadable row: {}", rowEx.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("buildFeeHeadBreakdownForReceipt - quantity lookup failed, falling back to plain amounts: {}", e.getMessage());
        }

        for (FeeSubmissionSub sub : feeSubmission.getFeeSubmissionSub()) {
            Map<String, Object> map = new HashMap<>();
            String feeHeadName = sub.getFeehead() != null && sub.getFeehead().getFeeHeadName() != null
                    ? sub.getFeehead().getFeeHeadName() : "";
            map.put("feehead", Map.of("feeHeadName", feeHeadName));
            map.put("amount", sub.getAmount());

            Long feeheadId = sub.getFeehead() != null ? sub.getFeehead().getId() : null;
            Integer qty = feeheadId != null ? quantityByFeeheadId.get(feeheadId) : null;
            if (qty != null && qty > 0 && sub.getAmount() != null) {
                BigDecimal rate = sub.getAmount().divide(BigDecimal.valueOf(qty), 2, RoundingMode.HALF_UP);
                map.put("quantity", qty);
                map.put("rate", rate);
            }
            result.add(map);
        }
        return result;
    }

    public FeeSubmission getFeeDetailsForReceipt(String receipt_no, School school, AcademicYear academicYear){
        log.info("Inside getFeeDetailsForReceipt");
        try{
            String finalReceiptNo = receipt_no.trim().replace("-","/");
            //Optional<FeeSubmission> feesubmission = feeSubmissionRepository.findByReceiptNoAndStatusAndSchool_IdAndAcademicYear_Id(receipt_no, "Active", school.getId(), academicYear.getId());
            FeeSubmission feesubmission = feeSubmissionRepository.findByReceiptNoIgnoreCaseAndStatusAndSchoolIdAndAcademicYearId(finalReceiptNo, "Active", school.getId(), academicYear.getId());
            log.debug("getFeeDetailsForReceipt - found={}", feesubmission != null);
            return feesubmission!=null? feesubmission : null;
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public Map calculateFeeSubmissionUserWise(Map<String, String> paramsMap, School school, AcademicYear academicYear){
        log.info("Inside calculateFeeSubmissionUserWise");
        Map responseMap  = new HashMap();
        try{
            Map<String, Object> finalDataMap = new HashMap<>();
            if(paramsMap!=null && !paramsMap.isEmpty()){
                if(paramsMap.containsKey("selectedOption")){
                    if(paramsMap.get("selectedOption").equalsIgnoreCase("today")){
                        String currentDate = paramsMap.get("todayDate");
                        log.debug("currentDate={}", currentDate);
                        List<Object[]> userWiseFeeCollection = feeSubmissionRepository.findFeeSubmissionAggregatesForCurrentDate(currentDate, school.getId(), academicYear.getId());
                        finalDataMap.put("userWiseFeeCollection", (CollectionUtils.isEmpty(userWiseFeeCollection))? "No Data found": userWiseFeeCollection);
                        List<FeeSubmission> todayFeeCollectionDetails = feeSubmissionRepository.findAllFeeDetailsByUser("Active", school.getId(), academicYear.getId(), currentDate, null, null);
                        if (CollectionUtils.isEmpty(todayFeeCollectionDetails)) {
                            finalDataMap.put("todayFeeCollectionDetails", "No Fee details found for current date:" + currentDate);
                        } else {
                            List<Map<String, Object>> leanList = new ArrayList<>();
                            for (FeeSubmission fs : todayFeeCollectionDetails) leanList.add(toLeanMap(fs));
                            finalDataMap.put("todayFeeCollectionDetails", leanList);
                        }

                        // Cancelled Fee Summary — same date, status = Inactive
                        List<Object[]> cancelledFeeSummary = feeSubmissionRepository.findCancelledFeeAggregatesForCurrentDate(currentDate, school.getId(), academicYear.getId());
                        finalDataMap.put("cancelledFeeSummary", (CollectionUtils.isEmpty(cancelledFeeSummary))? "No Data found": cancelledFeeSummary);
                        List<FeeSubmission> todayCancelledFeeDetails = feeSubmissionRepository.findAllFeeDetailsByUser("Inactive", school.getId(), academicYear.getId(), currentDate, null, null);
                        if (CollectionUtils.isEmpty(todayCancelledFeeDetails)) {
                            finalDataMap.put("todayCancelledFeeDetails", "No cancelled Fee details found for current date:" + currentDate);
                        } else {
                            List<Map<String, Object>> cancelledLeanList = new ArrayList<>();
                            for (FeeSubmission fs : todayCancelledFeeDetails) cancelledLeanList.add(toLeanMap(fs));
                            finalDataMap.put("todayCancelledFeeDetails", cancelledLeanList);
                        }
                    } else if(paramsMap.get("selectedOption").equalsIgnoreCase("range")){
                        String startDate = paramsMap.get("startDate");
                        String endDate = paramsMap.get("endDate");
                        log.debug("dateRange start={}, end={}", startDate, endDate);
                        List<Object[]> userWiseFeeCollection = feeSubmissionRepository.findFeeSubmissionAggregatesForDateRange(startDate, endDate, school.getId(), academicYear.getId());
                        finalDataMap.put("userWiseFeeCollection", (CollectionUtils.isEmpty(userWiseFeeCollection))? "No Data found": userWiseFeeCollection);
                        List<FeeSubmission> dateRangeFeeCollectionDetails = feeSubmissionRepository.findAllFeeDetailsByUser("Active", school.getId(), academicYear.getId(),  null, startDate, endDate);
                        if (CollectionUtils.isEmpty(dateRangeFeeCollectionDetails)) {
                            finalDataMap.put("dateRangeFeeCollectionDetails", "No Fee details found for dates:" + startDate + " and " + endDate);
                        } else {
                            List<Map<String, Object>> leanList = new ArrayList<>();
                            for (FeeSubmission fs : dateRangeFeeCollectionDetails) leanList.add(toLeanMap(fs));
                            finalDataMap.put("dateRangeFeeCollectionDetails", leanList);
                        }

                        // Cancelled Fee Summary — same date range, status = Inactive
                        List<Object[]> cancelledFeeSummary = feeSubmissionRepository.findCancelledFeeAggregatesForDateRange(startDate, endDate, school.getId(), academicYear.getId());
                        finalDataMap.put("cancelledFeeSummary", (CollectionUtils.isEmpty(cancelledFeeSummary))? "No Data found": cancelledFeeSummary);
                        List<FeeSubmission> dateRangeCancelledFeeDetails = feeSubmissionRepository.findAllFeeDetailsByUser("Inactive", school.getId(), academicYear.getId(), null, startDate, endDate);
                        if (CollectionUtils.isEmpty(dateRangeCancelledFeeDetails)) {
                            finalDataMap.put("dateRangeCancelledFeeDetails", "No cancelled Fee details found for dates:" + startDate + " and " + endDate);
                        } else {
                            List<Map<String, Object>> cancelledLeanList = new ArrayList<>();
                            for (FeeSubmission fs : dateRangeCancelledFeeDetails) cancelledLeanList.add(toLeanMap(fs));
                            finalDataMap.put("dateRangeCancelledFeeDetails", cancelledLeanList);
                        }
                    }
                }
            }
            responseMap.put("finalData", finalDataMap);
        }catch(Exception e){
            e.printStackTrace();
            responseMap.put("error", e.getLocalizedMessage());
        }
        return responseMap;
    }

    /**
     * Self-service "My Collection" report (FEE_REPORT_OWN_COLLECTION) — same shape as
     * calculateFeeSubmissionUserWise, but scoped to a single logged-in user's own Active
     * fee submissions only. Returns just Collection Summary + Collection Data (no cancelled-fee section).
     */
    public Map calculateFeeSubmissionForLoggedInUser(Map<String, String> paramsMap, School school, AcademicYear academicYear, Long createdById){
        log.info("Inside calculateFeeSubmissionForLoggedInUser");
        Map responseMap  = new HashMap();
        try{
            Map<String, Object> finalDataMap = new HashMap<>();
            if(paramsMap!=null && !paramsMap.isEmpty()){
                if(paramsMap.containsKey("selectedOption")){
                    if(paramsMap.get("selectedOption").equalsIgnoreCase("today")){
                        String currentDate = paramsMap.get("todayDate");
                        log.debug("currentDate={}", currentDate);
                        List<Object[]> userWiseFeeCollection = feeSubmissionRepository.findOwnFeeSubmissionAggregatesForCurrentDate(currentDate, school.getId(), academicYear.getId(), createdById);
                        finalDataMap.put("userWiseFeeCollection", (CollectionUtils.isEmpty(userWiseFeeCollection))? "No Data found": userWiseFeeCollection);
                        List<FeeSubmission> todayFeeCollectionDetails = feeSubmissionRepository.findAllFeeDetailsByUserAndCreatedBy("Active", school.getId(), academicYear.getId(), currentDate, null, null, createdById);
                        if (CollectionUtils.isEmpty(todayFeeCollectionDetails)) {
                            finalDataMap.put("todayFeeCollectionDetails", "No Fee details found for current date:" + currentDate);
                        } else {
                            List<Map<String, Object>> leanList = new ArrayList<>();
                            for (FeeSubmission fs : todayFeeCollectionDetails) leanList.add(toLeanMap(fs));
                            finalDataMap.put("todayFeeCollectionDetails", leanList);
                        }
                    } else if(paramsMap.get("selectedOption").equalsIgnoreCase("range")){
                        String startDate = paramsMap.get("startDate");
                        String endDate = paramsMap.get("endDate");
                        log.debug("dateRange start={}, end={}", startDate, endDate);
                        List<Object[]> userWiseFeeCollection = feeSubmissionRepository.findOwnFeeSubmissionAggregatesForDateRange(startDate, endDate, school.getId(), academicYear.getId(), createdById);
                        finalDataMap.put("userWiseFeeCollection", (CollectionUtils.isEmpty(userWiseFeeCollection))? "No Data found": userWiseFeeCollection);
                        List<FeeSubmission> dateRangeFeeCollectionDetails = feeSubmissionRepository.findAllFeeDetailsByUserAndCreatedBy("Active", school.getId(), academicYear.getId(), null, startDate, endDate, createdById);
                        if (CollectionUtils.isEmpty(dateRangeFeeCollectionDetails)) {
                            finalDataMap.put("dateRangeFeeCollectionDetails", "No Fee details found for dates:" + startDate + " and " + endDate);
                        } else {
                            List<Map<String, Object>> leanList = new ArrayList<>();
                            for (FeeSubmission fs : dateRangeFeeCollectionDetails) leanList.add(toLeanMap(fs));
                            finalDataMap.put("dateRangeFeeCollectionDetails", leanList);
                        }
                    }
                }
            }
            responseMap.put("finalData", finalDataMap);
        }catch(Exception e){
            e.printStackTrace();
            responseMap.put("error", e.getLocalizedMessage());
        }
        return responseMap;
    }

    public Map calculateCancelledFees(Map<String, String> paramsMap, School school, AcademicYear academicYear){
        log.info("Inside calculateCancelledFees");
        Map responseMap  = new HashMap();
        try{
            Map<String, Object> finalDataMap = new HashMap<>();
            if(paramsMap!=null && !paramsMap.isEmpty()){
                String startDate = paramsMap.get("startDate");
                String endDate = paramsMap.get("endDate");
                List<FeeSubmission> dateRangeFeeCollectionDetails = feeSubmissionRepository.findAllFeeDetailsBasedOnStatusAndInDateRange("Inactive", school.getId(), academicYear.getId(),  null, startDate, endDate);
                if (CollectionUtils.isEmpty(dateRangeFeeCollectionDetails)) {
                    finalDataMap.put("dateRangeFeeCollectionDetails", "No Fee details found for dates between:" + startDate + " and " + endDate);
                } else {
                    List<Map<String, Object>> leanList = new ArrayList<>();
                    for (FeeSubmission fs : dateRangeFeeCollectionDetails) leanList.add(toLeanMap(fs));
                    finalDataMap.put("dateRangeFeeCollectionDetails", leanList);
                }
            }
            responseMap.put("finalData", finalDataMap);
        }catch(Exception e){
            e.printStackTrace();
            responseMap.put("error", e.getLocalizedMessage());
        }
        return responseMap;
    }

    public Map calculateTotalSubmittedFees(Map<String, String> paramsMap, School school, AcademicYear academicYear){
        log.info("Inside calculateTotalSubmittedFees");
        Map responseMap  = new HashMap();
        try{
            Map<String, Object> finalDataMap = new HashMap<>();
            if(paramsMap!=null && !paramsMap.isEmpty()){
                log.debug("paramsMap={}", paramsMap);
                String medium = paramsMap.get("medium");

                List<FeeSubmission> totalFeeCollectionDetails = feeSubmissionRepository.findAllFeeSubmittedDetails(school.getId(), academicYear.getId(), Long.parseLong(medium));
                if (CollectionUtils.isEmpty(totalFeeCollectionDetails)) {
                    finalDataMap.put("totalFeeCollectionDetails", "No Fee details found for medium");
                } else {
                    List<Map<String, Object>> leanList = new ArrayList<>();
                    for (FeeSubmission fs : totalFeeCollectionDetails) leanList.add(toLeanMap(fs));
                    finalDataMap.put("totalFeeCollectionDetails", leanList);
                }
            }
            responseMap.put("finalData", finalDataMap);
        }catch(Exception e){
            e.printStackTrace();
            responseMap.put("error", e.getLocalizedMessage());
        }
        return responseMap;
    }

    public Map calculateTotalSubmittedFeesGradeWise(Map<String, String> paramsMap, School school, AcademicYear academicYear){
        log.info("Inside calculateTotalSubmittedFeesGradeWise");
        Map responseMap  = new HashMap();
        try{
            Map<String, Object> finalDataMap = new HashMap<>();
            if(paramsMap!=null && !paramsMap.isEmpty()){
                log.debug("paramsMap={}", paramsMap);
                String medium = paramsMap.get("medium");
                String section = paramsMap.get("section");
                String grade = paramsMap.get("grade");

                List<FeeSubmission> totalFeeCollectionDetails = feeSubmissionRepository.findAllFeeSubmittedDetailsGradeWise(school.getId(), academicYear.getId(), Long.parseLong(medium), Long.parseLong(grade), Long.parseLong(section));
                if (CollectionUtils.isEmpty(totalFeeCollectionDetails)) {
                    finalDataMap.put("totalFeeCollectionDetails", "No Fee details found for selected Grade-Section");
                } else {
                    List<Map<String, Object>> leanList = new ArrayList<>();
                    for (FeeSubmission fs : totalFeeCollectionDetails) leanList.add(toLeanMap(fs));
                    finalDataMap.put("totalFeeCollectionDetails", leanList);
                }
            }
            responseMap.put("finalData", finalDataMap);
        }catch(Exception e){
            e.printStackTrace();
            responseMap.put("error", e.getLocalizedMessage());
        }
        return responseMap;
    }

    public Map getSubmittedFeeDetailForGrade(School school, AcademicYear academicYear, Map<String, String> paramsMap){
        log.info("Inside getSubmittedFeeDetailForGrade");
        Map responseMap  = new HashMap();
        try{
            if(paramsMap!=null && !paramsMap.isEmpty()){
                String medium = paramsMap.get("medium");
                String section = paramsMap.get("section");
                String grade = paramsMap.get("grade");
                String acadmeicVal = paramsMap.get("academicYearId");
                Long academicId = 0L;
                if(acadmeicVal!=null && acadmeicVal.trim()!=""){
                    academicId = Long.valueOf(acadmeicVal);
                }
                SimpleDateFormat sf = new SimpleDateFormat("dd/MMM/yyyy");
                sf.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
                List<MonthMapping> mmList = monthmappingRepository.findAllByAcademicYear_IdAndSchool_IdOrderByPriorityAsc(academicId, school.getId());
                List<String> monthNamesList = mmList.stream()
                        .map(mm -> mm.getMonthMaster().getMonthName())
                        .collect(Collectors.toList());
                responseMap.put("MONTHS",monthNamesList);
                List<Long> orderedMonthIds = mmList.stream()
                        .map(mm -> mm.getMonthMaster().getId())
                        .collect(Collectors.toList());
                List<AcademicStudent> academicStudents = academicStudentRepository.findAllBySchool_IdAndMedium_IdAndGrade_IdAndSection_IdAndAcademicYear_Id(school.getId(), Long.parseLong(medium), Long.parseLong(grade), Long.parseLong(section), academicId);
                if(academicStudents!=null && !academicStudents.isEmpty()){
                    Map stuFeeSubMap = new HashMap();
                    for(AcademicStudent student:academicStudents){
                        // Admission Fee applies only to new-admission students, Annual Fee only to
                        // continuing/old students — mutually exclusive. Same rule already enforced in
                        // getMonthlyFeeTable/processFeeData/calculateFeeReminder/calculatePendingFeeSummary;
                        // this report (Total Deposited Fee) was missing it, so an old student's Admission
                        // Fee (never actually charged) was being summed into feeSubmitted below.
                        String feeTypeToExclude = "Old".equalsIgnoreCase(student.getStudent() != null ? student.getStudent().getStudentType() : "Old") ? "Admission Fee" : "Annual Fee";
                        Map stuFeeDataMap = new HashMap();
                        List<FeeSubmission> feeSubmissions = feeSubmissionRepository.findAllByAcademicStudent_IdAndStatus(student.getId(), "Active");
                        List depositedFeeList = new ArrayList();
                        int monthCount = 0;
                        if(feeSubmissions!=null && !feeSubmissions.isEmpty()){
                            //Calculate Fee + discount + Fine
                            BigDecimal fineAmount = BigDecimal.ZERO;

                            for(FeeSubmission feeSubmission:feeSubmissions){
                                int feeSubmissionMonthCounter = 0;
                                List<FeeSubmissionMonths> feeSubmissionMonths = feeSubmission.getFeeSubmissionMonths();
                                FeeSubmissionBalance feeSubmissionBalance = feeSubmission.getFeeSubmissionBalance();
                                /**
                                 * divide submitted fees by submitted months when every month fee calculated by fee mapped to class
                                 * calculate any discount, fine if applicable
                                 * */
                                BigDecimal paidAmount = feeSubmission.getPaidAmount();
                                BigDecimal discountAmt = feeSubmission.getDiscountAmount();
                                BigDecimal amountSubmitted = BigDecimal.valueOf(0.0);
                                BigDecimal discountReceived = BigDecimal.valueOf(0.0);
                                fineAmount = feeSubmission.getFineAmount();
                                amountSubmitted = paidAmount;
                                discountReceived = discountAmt;
                                if(feeSubmissionMonths!=null && feeSubmissionMonths.size()>0){
                                    //Months ids fetched which are submitted based on fee_submission

                                    //Calculating Fees based on month
                                    //List<Long> monthMasterIds = feeSubmissionMonths.stream().map(fsm -> fsm.getMonthMaster().getId()).collect(Collectors.toList());
                                    List<Long> monthMasterIds = feeSubmissionMonths.stream()
                                            .map(fsm -> fsm.getMonthMaster().getId())
                                            .sorted(Comparator.comparingInt(orderedMonthIds::indexOf))
                                            .collect(Collectors.toList());
                                    monthCount+=monthMasterIds.size();
                                    for(Long monthId:monthMasterIds){
                                        feeSubmissionMonthCounter++;
                                        List<Long> monthIdList = new ArrayList<>();
                                        monthIdList.add(monthId);
                                        Map feeDetailMap = new HashMap();
                                        feeDetailMap.put("receipt", feeSubmission.getReceiptNo());
                                        feeDetailMap.put("submitId", feeSubmission.getId());
                                        feeDetailMap.put("totalPaidAmount",paidAmount);
                                        feeDetailMap.put("totalDiscountAmount",discountAmt);
                                        feeDetailMap.put("feeSubmitted",BigDecimal.valueOf(0.0));
                                        BigDecimal discountAppliedForMonth = BigDecimal.ZERO;
                                        //Calculating Discount based on month
                                        if (discountAmt.compareTo(BigDecimal.ZERO) > 0 && feeSubmission.getDiscounthead()!=null) {
                                            List<Object[]> discountBasedOnMonths = discountclassmapRepository.findAmountAndDiscountHeadNames(academicId, school.getId(), monthIdList, Long.parseLong(grade), feeSubmission.getDiscounthead().getId(), Long.parseLong(medium));
                                            feeDetailMap.put("discountApplied", BigDecimal.valueOf(0.0));
                                            if(discountBasedOnMonths!=null && !discountBasedOnMonths.isEmpty()){
                                                discountAppliedForMonth = (discountBasedOnMonths.get(0)[0]!=null)?new BigDecimal(""+discountBasedOnMonths.get(0)[0]): BigDecimal.valueOf(0.0);
                                                feeDetailMap.put("discountApplied", discountAppliedForMonth);
                                                //discountReceived = discountAmt.subtract(discountAppliedForMonth);
                                            }
                                        }

                                        // Admin Special Discount (feeSubmission.migrationDiscountAmount) — NOT split or
                                        // subtracted month-by-month. Shown as a one-line callout on the last month of
                                        // the submission it belongs to, using the exact stored amount as entered.
                                        BigDecimal totalMigrationDiscount = feeSubmission.getMigrationDiscountAmount();
                                        if (totalMigrationDiscount != null && totalMigrationDiscount.compareTo(BigDecimal.ZERO) > 0
                                                && feeSubmissionMonthCounter == monthMasterIds.size()) {
                                            feeDetailMap.put("adminSpecialDiscountAmount", totalMigrationDiscount);
                                        }

                                        List<Object[]> feesBasedOnMonths = feeclassmapRepository.findAmountAndFeeHeadNames(academicId, school.getId(), monthIdList, Long.parseLong(grade), Long.parseLong(medium));
                                        BigDecimal amt = BigDecimal.ZERO;
                                        if(feesBasedOnMonths!=null && !feesBasedOnMonths.isEmpty()) {
                                            //fee heads + amount for selected months
                                            for(Object[] obj : feesBasedOnMonths){
                                                String feeHeadName = obj[1] != null ? obj[1].toString() : "";
                                                if (feeTypeToExclude.equalsIgnoreCase(feeHeadName)) {
                                                    continue;
                                                }
                                                BigDecimal amount = (obj[0] != null)
                                                        ? new BigDecimal(obj[0].toString())
                                                        : BigDecimal.ZERO;
                                                amt = amt.add(amount);
                                            }
                                            //BigDecimal feesubmitformonth = (feesBasedOnMonths.get(0)[0] != null) ? new BigDecimal("" + feesBasedOnMonths.get(0)[0]) : BigDecimal.valueOf(0.0);
                                            if (amt.compareTo(amountSubmitted) >= 0) {
                                                feeDetailMap.put("feeSubmitted", amt.subtract(discountAppliedForMonth));
                                            } else {
                                                amountSubmitted = paidAmount.subtract(amt);
                                                feeDetailMap.put("feeSubmitted", amt.subtract(discountAppliedForMonth));
                                            }
                                        }

                                        feeDetailMap.put("submitDate",sf.format(feeSubmission.getFeeSubmissionDate()));
                                        feeDetailMap.put("month_"+monthId.toString(),monthId.toString());
                                        feeDetailMap.put("fineAmount",fineAmount);
                                        feeDetailMap.put("academicStudent", toLeanAcademicStudentForFee(student));
                                        feeDetailMap.put("blankData",0);
                                        if(feeSubmissionMonthCounter==monthMasterIds.size()){
                                            feeDetailMap.put("balanceAmount", feeSubmissionBalance.getBalanceAmount());
                                        } else{
                                            feeDetailMap.put("balanceAmount", BigDecimal.valueOf(0.0));
                                        }
                                        fineAmount = BigDecimal.ZERO;
                                        depositedFeeList.add(feeDetailMap);
                                    }
                                }
                            }
                            if(monthCount<12){
                                depositedFeeList = setBlankForNotSubmittedMonth(monthCount, student, depositedFeeList);
                            }
                            stuFeeSubMap.put(""+student.getId(), depositedFeeList);
                        } else{
                            depositedFeeList = setBlankForNotSubmittedMonth(monthCount, student, depositedFeeList);
                            stuFeeSubMap.put(""+student.getId(), depositedFeeList);
                        }
                    }
                    responseMap.put("FEE_DATA", stuFeeSubMap);

                } else{
                    responseMap.put("NO_STUDENT_FOUND","No student found for selected grade.");
                }
            } else {
                //No class found
                responseMap.put("NO_PARAMS_FOUND","No selected parameters found.");
            }
        }catch(Exception e){
            e.printStackTrace();
            responseMap.put("error", e.getLocalizedMessage());
        }
        return responseMap;
    }

    public List setBlankForNotSubmittedMonth(int monthCount, AcademicStudent student, List depositedFeeList){
        log.info("Inside setBlankForNotSubmittedMonth");
        try{
            for(int k=monthCount;k<12;k++){
                Map feeDetailMap = new HashMap();
                feeDetailMap.put("receipt", "");
                feeDetailMap.put("submitId", "");
                feeDetailMap.put("totalPaidAmount", BigDecimal.valueOf(0.0));
                feeDetailMap.put("totalDiscountAmount",BigDecimal.valueOf(0.0));
                feeDetailMap.put("feeSubmitted",BigDecimal.valueOf(0.0));
                feeDetailMap.put("discountApplied", BigDecimal.valueOf(0.0));
                feeDetailMap.put("feeSubmitted", BigDecimal.valueOf(0.0));
                feeDetailMap.put("submitDate",null);
                feeDetailMap.put("month_"+(k+1),(k+1));
                feeDetailMap.put("fineAmount",BigDecimal.valueOf(0.0));
                feeDetailMap.put("academicStudent", toLeanAcademicStudentForFee(student));
                feeDetailMap.put("blankData",1);
                depositedFeeList.add(feeDetailMap);
            }
            return depositedFeeList;
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public BigDecimal getTodayFeeCollection(Long school, Long academic){
        log.info("Inside getTodayFeeCollection");
        try{
            BigDecimal totalFeeSubmitted = feeSubmissionRepository.getTodayTotalFeeSubmission(school, academic);
            if(totalFeeSubmitted.compareTo(BigDecimal.ZERO) > 0){
                return totalFeeSubmitted;
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return BigDecimal.ZERO;
    }

    public List calculateTotalGradewiseFees(Long school, Long academic){
        log.info("Inside calculateTotalGradewiseFees");
        try{
            // Fee-medium migration: headcount is now fetched per grade+section+medium (not
            // blended), and each medium's own tuition amount is applied to its own headcount,
            // then summed back up to ONE row per grade+section -- same output shape as before,
            // so the report template needs no change. This fixes a correctness bug: multiplying
            // a single flat (now ambiguous, since two mediums can price a grade differently) fee
            // amount by the section's WHOLE headcount would silently blend two mediums' prices
            // together once a section has students of both. Discount lookup below is completely
            // untouched -- still exactly one call per grade+section, so no double-counting risk.
            List<Object[]> gradeSectionMediumList = academicStudentRepository.getGradesAndSectionListByMedium(school, academic, "Active");
            log.debug("calculateTotalGradewiseFees - gradeSectionMediumList size={}", gradeSectionMediumList.size());
            if(!gradeSectionMediumList.isEmpty()){
                List<List> finalDataList = new ArrayList<>();

                // Aggregate rows back up to grade+section, keeping each section's per-medium
                // headcount around so the fee can be priced correctly per medium below.
                Map<String, Long> sectionTotalStudents = new LinkedHashMap<>();
                Map<String, Long> sectionGradeId = new HashMap<>();
                Map<String, Map<Long, Long>> sectionMediumHeadcount = new HashMap<>();
                Set<Long> gradeIds = new HashSet<>();
                Set<Long> mediumIds = new HashSet<>();

                for (Object[] row : gradeSectionMediumList) {
                    String gradeName = (String) row[0];
                    String sectionName = (String) row[1];
                    Long gradeId = (Long) row[2];
                    Long mediumId = (Long) row[4];
                    Long count = (Long) row[6];

                    String key = gradeName + "###" + sectionName;
                    sectionTotalStudents.merge(key, count, Long::sum);
                    sectionGradeId.put(key, gradeId);
                    sectionMediumHeadcount.computeIfAbsent(key, k -> new HashMap<>()).merge(mediumId, count, Long::sum);

                    gradeIds.add(gradeId);
                    mediumIds.add(mediumId);
                }

                // Per (gradeId, mediumId) -> tuition amount, one repository call per medium
                // actually present in the data (not per grade -- the query already groups by grade).
                Map<String, BigDecimal> gradeMediumFee = new HashMap<>();
                for (Long mediumId : mediumIds) {
                    List<Object[]> feeAmountDetails = feeSubmissionRepository.getGradewiseTutionFeesCurrentMonth(school, academic, new ArrayList<>(gradeIds), mediumId);
                    log.debug("feeAmountDetails for mediumId={} size={}", mediumId, feeAmountDetails == null ? 0 : feeAmountDetails.size());
                    if (feeAmountDetails != null) {
                        for (Object[] objLst : feeAmountDetails) {
                            if (objLst[1] == null) continue;
                            Long feeGradeId = ((Number) objLst[1]).longValue();
                            BigDecimal amt = objLst[0] != null ? new BigDecimal(objLst[0].toString()) : BigDecimal.ZERO;
                            gradeMediumFee.put(feeGradeId + "_" + mediumId, amt);
                        }
                    }
                }

                for (Map.Entry<String, Long> entry : sectionTotalStudents.entrySet()) {
                    String key = entry.getKey();
                    int sep = key.indexOf("###");
                    String gradeName = key.substring(0, sep);
                    String sectionName = key.substring(sep + 3);
                    Long gradeId = sectionGradeId.get(key);
                    Long noOfStudents = entry.getValue();

                    BigDecimal totalFeesIncome = BigDecimal.ZERO;
                    for (Map.Entry<Long, Long> medEntry : sectionMediumHeadcount.get(key).entrySet()) {
                        BigDecimal feeAmount = gradeMediumFee.getOrDefault(gradeId + "_" + medEntry.getKey(), BigDecimal.ZERO);
                        totalFeesIncome = totalFeesIncome.add(feeAmount.multiply(BigDecimal.valueOf(medEntry.getValue())));
                    }

                    List list = new ArrayList<>();
                    list.add(gradeName);
                    list.add(sectionName);
                    list.add(noOfStudents);
                    list.add(totalFeesIncome);
                    //Add discount detail
                    List<Object[]> discountDetails = feeSubmissionRepository.getStudentDiscountSummary(academic, school, gradeName, sectionName);
                    //List<Object[]> discountDetails = feeSubmissionRepository.getStudentDiscountSummary(school, academic, gradeName, section, monthId);
                    Long studentCountForDiscount = 0L;
                    BigDecimal studentAmountSumForDiscount = BigDecimal.ZERO;
                    if(discountDetails!=null && !discountDetails.isEmpty()){
                        for(Object[] discountDetail : discountDetails){
                            studentCountForDiscount += (Long)discountDetail[0];
                            studentAmountSumForDiscount = studentAmountSumForDiscount.add((BigDecimal) discountDetail[2]);
                        }
                    }
                    list.add(studentCountForDiscount);
                    list.add(studentAmountSumForDiscount);
                    BigDecimal incomeAmount  = totalFeesIncome.subtract(studentAmountSumForDiscount);
                    list.add(incomeAmount);
                    //add total discount fees
                    finalDataList.add(list);
                }
                //Get tution fee amount for classes
                log.debug("calculateTotalGradewiseFees - finalDataList size={}", finalDataList.size());
                return finalDataList;
            } else{
                new ArrayList<>();
            }

        }catch(Exception e){
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public Map cancelSubmittedFeeForStudent(Map<String, String> paramsMap, School school, AcademicYear academicYear){
        log.info("Inside cancelSubmittedFeeForStudent");
        Map<String, String> responseMap  = new HashMap();
        try{
            if(paramsMap!=null && !paramsMap.isEmpty()){
                //Get Student detail & max submitted fee id
                Long id = paramsMap.get("studentId")!=null?Long.parseLong(paramsMap.get("studentId")):0L;
                AcademicStudent student = academicStudentRepository.findByAcademicYearAndSchoolAndAcademicStudentId(academicYear.getId(), school.getId(), id);
                if(student!=null && student.getStatus().equalsIgnoreCase("active")){
                    Long feeId = paramsMap.get("feeId")!=null? Long.parseLong(paramsMap.get("feeId")):0L;
                    Long isLatest = feeSubmissionRepository.findLatestSubmissionId(id, "Active", academicYear.getId());
                    if (!feeId.equals(isLatest)) {
                        responseMap.put("error", "Only latest fee submission can be processed");
                    } else{
                        FeeSubmission feeSubmission = feeSubmissionRepository.findById(feeId).get();
                        feeSubmission.setStatus("Inactive");
                        feeSubmissionRepository.save(feeSubmission);
                        responseMap.put("success","Fee cancelled successfully for "+student.getStudent().getStudentName());
                    }
                } else{
                    //set response map
                    responseMap.put("error","Student not found/matched!");
                }
            } else{
                responseMap.put("error","No matching data found.");
            }
            log.debug("cancelSubmittedFeeForStudent responseMap keys={}", responseMap.keySet());
            //responseMap.put("finalData", finalDataMap);
        }catch(Exception e){
            e.printStackTrace();
            responseMap.put("error", e.getLocalizedMessage());
        }
        return responseMap;
    }

    private Long parseLongSafe(String value) {
        if (value == null || value.trim().isEmpty() || value.equals("null")) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Map calculateFeeSubmissionHeadWise(Map<String, String> paramsMap, School school, AcademicYear academicYear){
        log.info("Inside calculateFeeSubmissionHeadWise");
        Map responseMap  = new HashMap();
        try{
            Map<String, Object> finalDataMap = new HashMap<>();
            if(paramsMap!=null && !paramsMap.isEmpty()){
                if(paramsMap.containsKey("selectedOption")){
                    if(paramsMap.get("selectedOption").equalsIgnoreCase("today")){
                        String currentDate = paramsMap.get("todayDate");
                        log.debug("currentDate={}", currentDate);
                        List<Object[]> userWiseFeeCollection = feeSubmissionRepository.getFeeSubmissionHeadWiseToday(currentDate, school.getId(), academicYear.getId());
                        finalDataMap.put("userWiseFeeCollection", (CollectionUtils.isEmpty(userWiseFeeCollection))? "No Data found": userWiseFeeCollection);
                    } else if(paramsMap.get("selectedOption").equalsIgnoreCase("range")){
                        String startDate = paramsMap.get("startDate");
                        String endDate = paramsMap.get("endDate");
                        log.debug("dateRange start={}, end={}", startDate, endDate);
                        List<Object[]> userWiseFeeCollection = feeSubmissionRepository.getFeeSubmissionHeadWiseAggregatesForDateRange(startDate, endDate, school.getId(), academicYear.getId());
                        finalDataMap.put("userWiseFeeCollection", (CollectionUtils.isEmpty(userWiseFeeCollection))? "No Data found": userWiseFeeCollection);
                    } else if(paramsMap.get("selectedOption").equalsIgnoreCase("gradewise")){
                        String mediumId = paramsMap.get("mediumId");
                        String gradeId = paramsMap.get("gradeId");
                        String sectionId = paramsMap.get("sectionId");
                        String monthName = paramsMap.get("monthName");
                        Long mediumIdLong  = parseLongSafe(mediumId);
                        Long gradeIdLong   = parseLongSafe(gradeId);
                        Long sectionIdLong = parseLongSafe(sectionId);
                        List<Object[]> submissions = new ArrayList<>();;
                        if(monthName!=null && !monthName.isEmpty()){
                            if(!monthName.equalsIgnoreCase("all")){
                                submissions = feeSubmissionRepository
                                        .findByGradeWiseFiltersWithMonths(
                                                school.getId(),
                                                academicYear.getId(),
                                                mediumIdLong,
                                                gradeIdLong,
                                                sectionIdLong,
                                                monthName
                                        );
                            } else{
                                submissions = feeSubmissionRepository
                                        .findByGradeWiseFilters(
                                                school.getId(),
                                                academicYear.getId(),
                                                mediumIdLong,
                                                gradeIdLong,
                                                sectionIdLong
                                        );
                            }
                        }

                        finalDataMap.put("userWiseFeeCollection", (CollectionUtils.isEmpty(submissions))? "No Data found": submissions);
                    }
                }
            }
            responseMap.put("finalData", finalDataMap);
        }catch(Exception e){
            e.printStackTrace();
            responseMap.put("error", e.getLocalizedMessage());
        }
        return responseMap;
    }

    /**
     * Converts a FeeSubmission entity to a lean Map containing only the fields
     * required by the frontend reports. Prevents serializing the full JPA
     * object graph (school, academicYear, etc.) which can produce MB-size responses.
     */
    private Map<String, Object> toLeanMap(FeeSubmission fs) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", fs.getId());
        row.put("feeSubmissionDate", fs.getFeeSubmissionDate());
        row.put("receiptNo", fs.getReceiptNo());
        row.put("status", fs.getStatus());
        row.put("totalAmount", fs.getTotalAmount());
        row.put("paidAmount", fs.getPaidAmount());
        row.put("balanceAmount", fs.getBalanceAmount());
        row.put("fineAmount", fs.getFineAmount());
        row.put("discountAmount", fs.getDiscountAmount());
        row.put("paymentType", fs.getPaymentType());
        row.put("createdByName", fs.getCreatedByName());

        AcademicStudent as = fs.getAcademicStudent();
        if (as != null) {
            Map<String, Object> aMap = new HashMap<>();
            aMap.put("classSrNo", as.getClassSrNo());
            if (as.getGrade() != null) {
                aMap.put("grade", Map.of("gradeName", as.getGrade().getGradeName()));
            }
            if (as.getSection() != null) {
                aMap.put("section", Map.of("sectionName", as.getSection().getSectionName()));
            }
            if (as.getStudent() != null) {
                aMap.put("student", Map.of(
                    "studentName", as.getStudent().getStudentName() != null ? as.getStudent().getStudentName() : "",
                    "fatherName",  as.getStudent().getFatherName()  != null ? as.getStudent().getFatherName()  : ""
                ));
            }
            row.put("academicStudent", aMap);
        }

        List<Map<String, Object>> monthsList = new ArrayList<>();
        if (fs.getFeeSubmissionMonths() != null) {
            for (FeeSubmissionMonths fm : fs.getFeeSubmissionMonths()) {
                if (fm.getMonthMaster() != null) {
                    monthsList.add(Map.of("monthMaster", Map.of("monthName", fm.getMonthMaster().getMonthName())));
                }
            }
        }
        row.put("feeSubmissionMonths", monthsList);

        // Cash/Online split for collection reports (fees_user_collection.html /
        // fees_own_collection.html footer totals). Uses the breakup table when present
        // (covers Cash/Online/Both rows saved after that table was introduced); falls back
        // to the plain paymentType-based split for legacy rows saved before it existed -
        // "Both" never existed as an option back then, so that fallback is exactly correct
        // for every historical row, not an approximation.
        BigDecimal cashAmt = BigDecimal.ZERO;
        BigDecimal onlineAmt = BigDecimal.ZERO;
        if (fs.getPaymentBreakup() != null && !fs.getPaymentBreakup().isEmpty()) {
            for (FeeSubmissionPaymentBreakup b : fs.getPaymentBreakup()) {
                BigDecimal amt = b.getAmount() != null ? b.getAmount() : BigDecimal.ZERO;
                if ("Online".equalsIgnoreCase(b.getPaymentMode())) {
                    onlineAmt = onlineAmt.add(amt);
                } else {
                    cashAmt = cashAmt.add(amt);
                }
            }
        } else {
            BigDecimal paid = fs.getPaidAmount() != null ? fs.getPaidAmount() : BigDecimal.ZERO;
            if ("Online".equalsIgnoreCase(fs.getPaymentType())) {
                onlineAmt = paid;
            } else {
                cashAmt = paid;
            }
        }
        row.put("cashAmount", cashAmt);
        row.put("onlineAmount", onlineAmt);

        return row;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Pending Fee Summary Report
    // Returns aggregate data per grade-section for the selected months.
    // Security: gradeIds and monthIds are validated against school + academicYear
    //           so tampered IDs from other schools are silently ignored.
    // Performance: one batch SQL query per grade-section to count submitted months;
    //              fee_class_map loaded once per grade (cached in map).
    // ─────────────────────────────────────────────────────────────────────────────
    public Map<String, Object> calculatePendingFeeSummary(Map<String, String> requestBody, School school, AcademicYear academicYear) {
        log.info("Inside calculatePendingFeeSummary");
        Map<String, Object> result = new HashMap<>();
        try {
            // ── 1. Parse inputs ────────────────────────────────────────────────
            Long mediumId = parseLongSafe(requestBody.get("mediumId")); // null = All Mediums
            String gradeIdsStr   = requestBody.getOrDefault("gradeIds", "");
            String monthIdsStr   = requestBody.getOrDefault("monthIds", "");
            String sectionIdsStr = requestBody.getOrDefault("sectionIds", "");

            if (gradeIdsStr.isEmpty() || monthIdsStr.isEmpty()) {
                result.put("error", "Grades and months are required.");
                return result;
            }

            List<Long> selectedGradeIds = Arrays.stream(gradeIdsStr.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(Long::parseLong).collect(Collectors.toList());

            List<Long> selectedMonthIds = Arrays.stream(monthIdsStr.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(Long::parseLong).collect(Collectors.toList());

            List<Long> selectedSectionIds = sectionIdsStr.isEmpty() ? Collections.emptyList() :
                    Arrays.stream(sectionIdsStr.split(","))
                            .map(String::trim).filter(s -> !s.isEmpty())
                            .map(Long::parseLong).collect(Collectors.toList());

            int selectedMonthCount = selectedMonthIds.size();

            // ── 2. Pre-load Fine and fee-due date (once for all students) ─────
            Fine fine = null;
            try {
                List<Fine> fines = fineRepository.findAllByAcademicYear_IdAndSchool_Id(academicYear.getId(), school.getId());
                if (fines != null && !fines.isEmpty()) fine = fines.get(0);
            } catch (Exception ignore) {}

            int cdiff = 0; // positive = fee date not yet passed; negative = overdue
            FeeDate feeDate = null;
            try {
                List<FeeDate> feeDates = feedateRepository.findByAcademicYearAndSchoolAndGivenMonth(
                        academicYear.getId(), school.getId(), LocalDate.now().getMonthValue());
                if (feeDates != null && !feeDates.isEmpty()) {
                    feeDate = feeDates.get(0);
                    cdiff = monthmappingRepository.currentFeeDateDifference(
                            new SimpleDateFormat("dd/MMM/yyyy").format(feeDate.getFeeSubmissiondate()),
                            new SimpleDateFormat("dd/MMM/yyyy").format(new Date()));
                }
            } catch (Exception ignore) {}
            final int finalCdiff = cdiff;
            final Fine finalFine = fine;

            // ── 3. Load all active student discounts for this school+year ─────
            List<StudentDiscount> allDiscounts = studentDiscountRepository
                    .findAllBySchool_IdAndAcademicYear_IdAndStatus(school.getId(), academicYear.getId(), "Active");
            Map<Long, StudentDiscount> discountByStudentId = new HashMap<>();
            if (allDiscounts != null) {
                for (StudentDiscount sd : allDiscounts) {
                    if (sd.getAcademicStudent() != null)
                        discountByStudentId.put(sd.getAcademicStudent().getId(), sd);
                }
            }

            // ── 4. Build monthId → monthName map (for fine calculation) ───────
            Map<Long, String> monthIdToName = new HashMap<>();
            List<MonthMapping> allMMs = monthmappingRepository
                    .findAllByAcademicYear_IdAndSchool_IdOrderByPriorityAsc(academicYear.getId(), school.getId());
            for (MonthMapping mm : allMMs) {
                monthIdToName.put(mm.getMonthMaster().getId(), mm.getMonthMaster().getMonthName());
            }

            // ── 5. Per-grade caches ───────────────────────────────────────────
            // gradeFeePerMonth: "gradeId_mediumId" → monthId → list of [amount, headName]
            // (medium-keyed since fee-medium migration; mediumId component may itself be "null" as a string)
            Map<String, Map<Long, List<Object[]>>> gradeFeePerMonth = new HashMap<>();
            // gradeDiscountPerHead: gradeId → discountHeadId → [totalDiscount for ALL selectedMonths]
            // We will compute on-demand per student with their specific unpaid months
            // Fine cache: firstUnpaidMonthName → fine amount
            Map<String, BigDecimal> fineCacheByFirstMonth = new HashMap<>();

            // ── 6. Main loop ──────────────────────────────────────────────────
            List<Object[]> gradeSectionRows = academicStudentRepository
                    .getGradesAndSectionList(school.getId(), academicYear.getId(), "Active");

            List<Map<String, Object>> rows = new ArrayList<>();
            BigDecimal grandTotalPendingAmount  = BigDecimal.ZERO;
            long grandTotalPendingStudents = 0;
            long grandTotalStudents        = 0;

            for (Object[] row : gradeSectionRows) {
                String gradeName   = (String) row[0];
                String sectionName = (String) row[1];
                Long   gradeId     = (Long)   row[2];
                Long   sectionId   = (Long)   row[3];

                if (!selectedGradeIds.contains(gradeId)) continue;
                if (!selectedSectionIds.isEmpty() && !selectedSectionIds.contains(sectionId)) continue;

                // ── 6a. Per-month fee details are cached per (grade, student's own medium),
                // not per grade alone — fee-medium migration: a grade/section block can now
                // contain students of different mediums, so the lookup is lazy and keyed per
                // student inside the loop below (6d), rather than pre-computed once here.

                // ── 6b. Fetch students ────────────────────────────────────────
                List<AcademicStudent> students = (mediumId != null)
                        ? academicStudentRepository.findAllBySchool_IdAndMedium_IdAndGrade_IdAndSection_IdAndAcademicYear_IdAndStatusIgnoreCase(
                                school.getId(), mediumId, gradeId, sectionId, academicYear.getId(), "Active")
                        : academicStudentRepository.findAllBySchool_IdAndGrade_IdAndSection_IdAndAcademicYear_IdAndStatusIgnoreCase(
                                school.getId(), gradeId, sectionId, academicYear.getId(), "Active");

                if (students == null || students.isEmpty()) continue;
                long totalStu = students.size();

                // ── 6c. Batch: which months has each student paid ─────────────
                List<Long> studentIds = students.stream().map(AcademicStudent::getId).collect(Collectors.toList());
                List<Object[]> paidPairs = feeSubmissionRepository
                        .getSubmittedMonthsForStudents(school.getId(), academicYear.getId(), studentIds, selectedMonthIds);

                Map<Long, Set<Long>> studentPaidMonths = new HashMap<>();
                if (paidPairs != null) {
                    for (Object[] p : paidPairs) {
                        Long stuId  = ((Number) p[0]).longValue();
                        Long mId    = ((Number) p[1]).longValue();
                        studentPaidMonths.computeIfAbsent(stuId, k -> new HashSet<>()).add(mId);
                    }
                }

                // ── 6c2. Batch: latest carry-forward balance per student ───────
                // getLatestBalanceAmountsForStudents only returns a row when balance_amount > 0,
                // so a student absent from this map is ambiguous on its own: either they've
                // submitted this year and are fully paid up (balance genuinely 0), or they've
                // never submitted at all this year (in which case their opening balance — dues
                // carried from a previous year/system — still applies and must not be dropped).
                // findAcademicStudentIdsWithAnySubmission disambiguates the two below.
                List<Object[]> balancePairs = feeSubmissionRepository
                        .getLatestBalanceAmountsForStudents(school.getId(), academicYear.getId(), studentIds);
                Map<Long, BigDecimal> studentBalance = new HashMap<>();
                if (balancePairs != null) {
                    for (Object[] bp : balancePairs) {
                        Long stuId = ((Number) bp[0]).longValue();
                        BigDecimal bal = bp[1] != null ? new BigDecimal(bp[1].toString()) : BigDecimal.ZERO;
                        studentBalance.put(stuId, bal);
                    }
                }
                Set<Long> studentsWithAnySubmission = new HashSet<>(
                        feeSubmissionRepository.findAcademicStudentIdsWithAnySubmission(
                                school.getId(), academicYear.getId(), studentIds));

                // ── 6d. Per-student accurate calculation ──────────────────────
                long pendingStudents  = 0;
                BigDecimal pendingAmt = BigDecimal.ZERO;

                for (AcademicStudent stu : students) {
                    Set<Long> paid = studentPaidMonths.getOrDefault(stu.getId(), Collections.emptySet());
                    List<Long> unpaidMonthIds = selectedMonthIds.stream()
                            .filter(m -> !paid.contains(m)).collect(Collectors.toList());
                    BigDecimal stuBalance;
                    if (studentBalance.containsKey(stu.getId())) {
                        stuBalance = studentBalance.get(stu.getId());
                    } else if (studentsWithAnySubmission.contains(stu.getId())) {
                        // Has submitted this year already, just fully paid up — genuinely 0, not opening balance.
                        stuBalance = BigDecimal.ZERO;
                    } else {
                        // Never submitted this year — fall back to opening balance (dues carried
                        // from a previous year/system), same rule as the Fee Submission screen.
                        stuBalance = stu.getOpeningBalance() != null ? stu.getOpeningBalance() : BigDecimal.ZERO;
                    }

                    if (unpaidMonthIds.isEmpty()) {
                        // All selected months paid — count only if carry-forward balance exists
                        if (stuBalance.compareTo(BigDecimal.ZERO) > 0) {
                            pendingStudents++;
                            pendingAmt = pendingAmt.add(stuBalance);
                        }
                        continue;
                    }

                    pendingStudents++;

                    // Fee for unpaid months (exclude one-time fee per student type)
                    String feeTypeToExclude = "Old".equalsIgnoreCase(
                            stu.getStudent() != null ? stu.getStudent().getStudentType() : "Old")
                            ? "Admission Fee" : "Annual Fee";

                    // Fee-medium migration: fee amounts now vary by medium, so the per-grade
                    // cache is re-keyed per (grade, student's own medium) and populated lazily
                    // here on first use — keyed by each student's ACTUAL medium (AcademicStudent),
                    // not the report-level mediumId filter, which can be null ("All Mediums" view).
                    Long stuMediumId = stu.getMedium() != null ? stu.getMedium().getId() : null;
                    String feeCacheKey = gradeId + "_" + stuMediumId;
                    Map<Long, List<Object[]>> feeByMonth = gradeFeePerMonth.get(feeCacheKey);
                    if (feeByMonth == null) {
                        List<Object[]> feeRows = (stuMediumId != null)
                                ? feeclassmapRepository.findFeeDetailsPerMonth(
                                        academicYear.getId(), school.getId(), selectedMonthIds, gradeId, stuMediumId)
                                : feeclassmapRepository.findFeeDetailsPerMonth(
                                        academicYear.getId(), school.getId(), selectedMonthIds, gradeId);
                        feeByMonth = new HashMap<>();
                        if (feeRows != null) {
                            for (Object[] fr : feeRows) {
                                Long mId = ((Number) fr[2]).longValue();
                                feeByMonth.computeIfAbsent(mId, k -> new ArrayList<>()).add(fr);
                            }
                        }
                        gradeFeePerMonth.put(feeCacheKey, feeByMonth);
                    }

                    BigDecimal stuFee = BigDecimal.ZERO;
                    for (Long mId : unpaidMonthIds) {
                        List<Object[]> monthFees = feeByMonth.getOrDefault(mId, Collections.emptyList());
                        for (Object[] fd : monthFees) {
                            String headName = fd[1] != null ? fd[1].toString() : "";
                            if (!feeTypeToExclude.equalsIgnoreCase(headName) && fd[0] != null) {
                                stuFee = stuFee.add((BigDecimal) fd[0]);
                            }
                        }
                    }

                    // Discount for unpaid months
                    BigDecimal discountAmt = BigDecimal.ZERO;
                    StudentDiscount stuDiscount = discountByStudentId.get(stu.getId());
                    if (stuDiscount != null) {
                        try {
                            // Discount-medium migration: reuses stuMediumId already resolved above
                            // for the fee lookup, with the same null-safe fallback.
                            List<Object[]> disRows = stuMediumId != null
                                    ? discountclassmapRepository.findAmountAndDiscountHeadNames(
                                            academicYear.getId(), school.getId(), unpaidMonthIds,
                                            gradeId, stuDiscount.getDiscounthead().getId(), stuMediumId)
                                    : discountclassmapRepository.findAmountAndDiscountHeadNames(
                                            academicYear.getId(), school.getId(), unpaidMonthIds,
                                            gradeId, stuDiscount.getDiscounthead().getId());
                            if (disRows != null) {
                                for (Object[] dr : disRows) {
                                    if (dr[0] != null) discountAmt = discountAmt.add((BigDecimal) dr[0]);
                                }
                            }
                        } catch (Exception ignore) {}
                    }

                    // Fine for unpaid months (based on oldest unpaid month)
                    BigDecimal fineAmt = BigDecimal.ZERO;
                    if (finalFine != null && !unpaidMonthIds.isEmpty()) {
                        try {
                            // Use first unpaid month's name as fine anchor (same logic as detail report)
                            Long firstUnpaidMonthId = unpaidMonthIds.get(0);
                            String firstMonthName = monthIdToName.getOrDefault(firstUnpaidMonthId, "");
                            if (!firstMonthName.isEmpty()) {
                                BigDecimal cachedFine = fineCacheByFirstMonth.get(firstMonthName);
                                if (cachedFine == null) {
                                    int monthDiff = monthmappingRepository.findMonthDifference(
                                            academicYear.getId(), school.getId(), firstMonthName,
                                            new SimpleDateFormat("dd/MMM/yyyy").format(new Date()));
                                    int fineMultiplier = Math.max(0, monthDiff + (finalCdiff < 0 ? 1 : 0));
                                    if (fineMultiplier >= finalFine.getMaxCalculated()) {
                                        cachedFine = BigDecimal.valueOf(finalFine.getFineAmount())
                                                .multiply(BigDecimal.valueOf(finalFine.getMaxCalculated()));
                                    } else {
                                        cachedFine = BigDecimal.valueOf(finalFine.getFineAmount())
                                                .multiply(BigDecimal.valueOf(fineMultiplier));
                                    }
                                    fineCacheByFirstMonth.put(firstMonthName, cachedFine);
                                }
                                fineAmt = cachedFine;
                            }
                        } catch (Exception ignore) {}
                    }

                    // Final: balance + fee + fine - discount (never go below zero)
                    BigDecimal stuTotal = stuBalance.add(stuFee).add(fineAmt).subtract(discountAmt);
                    if (stuTotal.compareTo(BigDecimal.ZERO) < 0) stuTotal = BigDecimal.ZERO;
                    pendingAmt = pendingAmt.add(stuTotal);
                }

                double pendingPercent = totalStu > 0
                        ? Math.round((pendingStudents * 100.0 / totalStu) * 10.0) / 10.0
                        : 0.0;

                Map<String, Object> rowMap = new LinkedHashMap<>();
                rowMap.put("gradeName",       gradeName);
                rowMap.put("sectionName",     sectionName);
                rowMap.put("gradeId",         gradeId);
                rowMap.put("sectionId",       sectionId);
                rowMap.put("totalStudents",   totalStu);
                rowMap.put("pendingStudents", pendingStudents);
                rowMap.put("pendingAmount",   pendingAmt);
                rowMap.put("pendingPercent",  pendingPercent);
                rows.add(rowMap);

                grandTotalPendingAmount   = grandTotalPendingAmount.add(pendingAmt);
                grandTotalPendingStudents += pendingStudents;
                grandTotalStudents        += totalStu;
            }

            double grandPendingPercent = grandTotalStudents > 0
                    ? Math.round((grandTotalPendingStudents * 100.0 / grandTotalStudents) * 10.0) / 10.0
                    : 0.0;

            result.put("rows",                     rows);
            result.put("grandTotalPendingAmount",  grandTotalPendingAmount);
            result.put("grandTotalPendingStudents",grandTotalPendingStudents);
            result.put("grandTotalStudents",       grandTotalStudents);
            result.put("grandPendingPercent",      grandPendingPercent);

        } catch (Exception e) {
            e.printStackTrace();
            result.put("error", "Error generating summary: " + e.getMessage());
        }
        return result;
    }

    private Map<String, Object> toLeanAcademicStudentForFee(AcademicStudent as) {
        Map<String, Object> aMap = new HashMap<>();
        if (as == null) return aMap;
        aMap.put("id", as.getId());
        aMap.put("classSrNo", as.getClassSrNo());
        aMap.put("status", as.getStatus() != null ? as.getStatus() : "");
        if (as.getGrade() != null) {
            Map<String, Object> gradeMap = new HashMap<>();
            gradeMap.put("id", as.getGrade().getId());
            gradeMap.put("gradeName", as.getGrade().getGradeName() != null ? as.getGrade().getGradeName() : "");
            aMap.put("grade", gradeMap);
        }
        if (as.getSection() != null) {
            Map<String, Object> secMap = new HashMap<>();
            secMap.put("id", as.getSection().getId());
            secMap.put("sectionName", as.getSection().getSectionName() != null ? as.getSection().getSectionName() : "");
            aMap.put("section", secMap);
        }
        if (as.getStudent() != null) {
            Map<String, Object> stuMap = new HashMap<>();
            stuMap.put("studentName", as.getStudent().getStudentName() != null ? as.getStudent().getStudentName() : "");
            stuMap.put("fatherName",  as.getStudent().getFatherName()  != null ? as.getStudent().getFatherName()  : "");
            stuMap.put("motherName",  as.getStudent().getMotherName()  != null ? as.getStudent().getMotherName()  : "");
            stuMap.put("mobile1",     as.getStudent().getMobile1()     != null ? as.getStudent().getMobile1()     : "");
            stuMap.put("status",      as.getStudent().getStatus()      != null ? as.getStudent().getStatus()      : "");
            aMap.put("student", stuMap);
        }
        return aMap;
    }

}
