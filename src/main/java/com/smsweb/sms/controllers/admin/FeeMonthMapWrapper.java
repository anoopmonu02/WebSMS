package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.models.admin.FeeClassMap;
import com.smsweb.sms.models.admin.FeeMonthMap;
import lombok.Data;

import java.util.List;

@Data
public class FeeMonthMapWrapper {
    private List<FeeMonthMap> feeMonthMaps;
}
