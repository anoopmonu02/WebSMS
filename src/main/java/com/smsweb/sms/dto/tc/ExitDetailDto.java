package com.smsweb.sms.dto.tc;

import com.smsweb.sms.models.tc.enums.ExitReason;
import com.smsweb.sms.models.tc.enums.ExitStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** Everything the exit detail page shows. Plain values only, so the page never touches lazy data. */
@Getter
@Setter
public class ExitDetailDto {

    private UUID uuid;
    private ExitStatus status;
    /** Public id of the student record, for the link to the timeline page. */
    private UUID studentUuid;

    // student and class
    private String studentName;
    private String fatherName;
    private String motherName;
    private String registrationNo;
    private String srNo;
    private String className;
    private String sectionName;
    private String mediumName;
    private String sessionName;

    // exit
    private ExitReason exitReason;
    private Integer absentDays;
    private LocalDate exitDate;
    private boolean includeLastMonth;
    private String remark;
    private String createdByName;
    private LocalDateTime creationDate;
    private boolean loginDisabledByExit;

    // dues
    private BigDecimal calcAmount;
    private Map<String, Object> calcBreakdown;
    private boolean duesManual;
    private String duesManualReason;
    private boolean duesFrozen;
    private BigDecimal frozenAmount;
    private LocalDateTime frozenAt;

    // approval
    private String approvedByName;
    private LocalDateTime approvedAt;
    private String approvalRemark;

    // cancel
    private String cancelledByName;
    private LocalDateTime cancelledAt;
    private String cancelReason;

    // reactivation (same session, after the TC was collected); read from the timeline row
    private String reactivatedByName;
    private LocalDateTime reactivatedAt;
    private String reactivateReason;

    // what the page may offer
    /** DISSOLVE / DISSOLVE_AND_CREATE chosen at strike-off; null when the student had no sibling group. */
    private String siblingAction;
    private boolean canApprove;
    private boolean canCancel;
    private boolean canReactivate;
    /** TC collected in an earlier session: the student can be registered again in the current session. */
    private boolean canReRegister;

    // set once the student was registered again (status RE_REGISTERED)
    private UUID newStudentUuid;
    private String newRegistrationNo;
}
