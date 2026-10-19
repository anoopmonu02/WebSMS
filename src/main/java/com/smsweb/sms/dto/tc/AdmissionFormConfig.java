package com.smsweb.sms.dto.tc;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

/**
 * What the registration page needs to show the admission block: whether the rule is on, and which
 * grades are in the "up to class 5" and "class 6" tiers. Every other grade is treated as mandatory.
 */
@Getter
@AllArgsConstructor
public class AdmissionFormConfig {

    private final boolean enabled;
    private final Set<Long> exemptGradeIds;
    private final Set<Long> notaryGradeIds;

    public static AdmissionFormConfig off() {
        return new AdmissionFormConfig(false, Set.of(), Set.of());
    }
}
