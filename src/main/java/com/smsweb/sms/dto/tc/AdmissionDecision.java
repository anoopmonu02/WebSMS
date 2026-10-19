package com.smsweb.sms.dto.tc;

import com.smsweb.sms.models.tc.enums.AdmissionBasis;

/**
 * Outcome of the admission rule for one new student: on what basis the admission was accepted,
 * and the value to record on the timeline (TC number, notary document number, or the remark).
 */
public record AdmissionDecision(AdmissionBasis basis, String reference, String remark) {
}
