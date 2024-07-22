package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.models.admin.DiscountMonthMap;
import com.smsweb.sms.models.admin.FeeMonthMap;
import lombok.Data;

import java.util.List;

@Data
public class DiscountMonthMapWrapper {
    private List<DiscountMonthMap> discountMonthMaps;
}
