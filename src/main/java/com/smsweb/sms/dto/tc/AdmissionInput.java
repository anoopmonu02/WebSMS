package com.smsweb.sms.dto.tc;

/**
 * What the registration form sends for the admission rule. Plain request values, not fields of
 * {@code Student}: the TC number is the existing {@code tcNo} field, the rest are extra inputs.
 */
public record AdmissionInput(String tcNo,
                             boolean notaryAffidavit,
                             String notaryDocNo,
                             boolean exemptAdmission,
                             String exemptionRemark) {
}
