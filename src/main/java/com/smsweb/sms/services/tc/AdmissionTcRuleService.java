package com.smsweb.sms.services.tc;

import com.smsweb.sms.dto.tc.AdmissionDecision;
import com.smsweb.sms.dto.tc.AdmissionFormConfig;
import com.smsweb.sms.dto.tc.AdmissionInput;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Student;
import com.smsweb.sms.models.tc.enums.AdmissionBasis;
import com.smsweb.sms.models.tc.enums.EventSource;
import com.smsweb.sms.models.tc.enums.EventType;
import com.smsweb.sms.repositories.tc.StudentLookupRepository;
import com.smsweb.sms.services.users.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;

/**
 * Admission TC rule for the web registration form (plan 6.8).
 *
 * <p>{@link #evaluate} runs before the student is saved and only validates; it writes nothing and
 * returns {@code null} when the TC feature or the rule is off, so the form then behaves exactly as
 * before. {@link #recordBasis} runs after a successful save and writes one timeline row.</p>
 *
 * <p>Tiers come from the three grade lists in TC Settings. A grade in none of the lists is
 * treated as mandatory (TC required). If a grade is wrongly in more than one list, the stricter
 * tier wins.</p>
 */
@Service
public class AdmissionTcRuleService {

    private static final Logger log = LoggerFactory.getLogger(AdmissionTcRuleService.class);

    private static final int MAX_TC_NO_LENGTH = 100;
    private static final int MAX_DOC_NO_LENGTH = 100;
    private static final int MAX_REMARK_LENGTH = 400;

    private enum Tier { EXEMPT, NOTARY, MANDATORY }

    private final TcConfigService tcConfigService;
    private final StudentEventService studentEventService;
    private final StudentLookupRepository studentLookupRepository;
    private final UserService userService;

    public AdmissionTcRuleService(TcConfigService tcConfigService,
                                  StudentEventService studentEventService,
                                  StudentLookupRepository studentLookupRepository,
                                  UserService userService) {
        this.tcConfigService = tcConfigService;
        this.studentEventService = studentEventService;
        this.studentLookupRepository = studentLookupRepository;
        this.userService = userService;
    }

    /** True when the feature and the admission rule are both on for the school. */
    public boolean isActive(School school) {
        return school != null && tcConfigService.isEnabled(school) && tcConfigService.admissionRuleEnabled(school);
    }

    /** For the registration page: switch plus the two lists that are not mandatory. */
    public AdmissionFormConfig formConfig(School school) {
        if (!isActive(school)) {
            return AdmissionFormConfig.off();
        }
        return new AdmissionFormConfig(true,
                tcConfigService.exemptGradeIds(school),
                tcConfigService.notaryGradeIds(school));
    }

    /**
     * Checks the admission of a new student against the rule.
     *
     * @return {@code null} when the rule does not apply; otherwise the accepted basis
     * @throws TcValidationException with a message for the user when the requirement is not met
     */
    public AdmissionDecision evaluate(Long gradeId, AdmissionInput input, School school) {
        if (!isActive(school)) {
            return null;
        }
        if (gradeId == null) {
            throw new TcValidationException("Select the grade first.");
        }
        String tcNo = clean(input == null ? null : input.tcNo());
        if (tcNo != null && tcNo.length() > MAX_TC_NO_LENGTH) {
            throw new TcValidationException("TC number is too long.");
        }
        Tier tier = tierOf(gradeId, school);

        // A TC number is accepted in every tier and needs no exemption record.
        if (tcNo != null) {
            return new AdmissionDecision(AdmissionBasis.TC, tcNo.toUpperCase(), "TC of the previous school");
        }
        switch (tier) {
            case EXEMPT: {
                String remark = clean(input == null ? null : input.exemptionRemark());
                if (remark == null) {
                    throw new TcValidationException("This class can be admitted without a TC, but the reason is required. "
                            + "Enter the reason for admitting without a TC.");
                }
                if (remark.length() > MAX_REMARK_LENGTH) {
                    throw new TcValidationException("The reason for admitting without a TC is too long (maximum "
                            + MAX_REMARK_LENGTH + " characters).");
                }
                return new AdmissionDecision(AdmissionBasis.EXEMPT_ADMIN, null, remark);
            }
            case NOTARY: {
                String docNo = clean(input == null ? null : input.notaryDocNo());
                if (input == null || !input.notaryAffidavit() || docNo == null) {
                    throw new TcValidationException("This class needs a TC number, or a notary affidavit with its document number.");
                }
                if (docNo.length() > MAX_DOC_NO_LENGTH) {
                    throw new TcValidationException("Notary document number is too long.");
                }
                return new AdmissionDecision(AdmissionBasis.NOTARY, docNo, "Notary affidavit instead of TC");
            }
            default:
                throw new TcValidationException("A TC number is required to admit a student in this class.");
        }
    }

    /**
     * Writes the timeline row for an accepted admission. Joins the caller's transaction or starts
     * its own. The caller treats a failure here as non-fatal: the student is already saved.
     */
    @Transactional
    public void recordBasis(Student saved, AcademicYear year, AdmissionDecision decision) {
        if (decision == null || saved == null || saved.getId() == null || year == null) {
            return;
        }
        School school = saved.getSchool();
        AcademicStudent enrollment = null;
        if (school != null) {
            enrollment = studentLookupRepository
                    .findEnrollmentsOfStudentInYear(saved.getId(), year.getId(), school.getId())
                    .stream().findFirst().orElse(null);
        }
        UserEntity user = userService.getLoggedInUser();
        EventType type;
        switch (decision.basis()) {
            case NOTARY:
                type = EventType.ADMISSION_NOTARY;
                break;
            case EXEMPT_ADMIN:
                type = EventType.ADMISSION_EXEMPTED;
                break;
            default:
                type = EventType.ADMISSION_TC_SUBMITTED;
                break;
        }
        studentEventService.log(type, saved, enrollment, year, LocalDate.now(),
                null, decision.reference(), decision.remark(), null, null, EventSource.WEB, user);
        log.info("Admission basis {} recorded for studentId={}", decision.basis(), saved.getId());
    }

    private Tier tierOf(Long gradeId, School school) {
        Set<Long> mandatory = tcConfigService.mandatoryGradeIds(school);
        if (mandatory.contains(gradeId)) {
            return Tier.MANDATORY;
        }
        if (tcConfigService.notaryGradeIds(school).contains(gradeId)) {
            return Tier.NOTARY;
        }
        if (tcConfigService.exemptGradeIds(school).contains(gradeId)) {
            return Tier.EXEMPT;
        }
        return Tier.MANDATORY;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
