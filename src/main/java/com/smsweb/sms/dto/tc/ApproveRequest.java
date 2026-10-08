package com.smsweb.sms.dto.tc;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Input of the approve (green flag) dialog. Leave {@code manualAmount} empty to accept the
 * estimate; give an amount and a reason to replace it.
 */
@Getter
@Setter
public class ApproveRequest {

    private String remark;
    private BigDecimal manualAmount;
    private String manualReason;
}
