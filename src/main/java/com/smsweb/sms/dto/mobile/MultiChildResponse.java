package com.smsweb.sms.dto.mobile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Returned when a parent mobile number is linked to 2+ children.
 * Flutter shows a child picker; the user then calls /auth/select-child.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiChildResponse {
    private String loginType;        // always "MULTI_CHILD"
    private String tempToken;        // UUID, valid for 10 minutes, single-use
    private boolean mustChangePassword;
    private List<ChildSummaryDto> children;
}
