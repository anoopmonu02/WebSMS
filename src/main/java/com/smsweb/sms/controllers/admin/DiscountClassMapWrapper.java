package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.models.admin.DiscountClassMap;
import com.smsweb.sms.models.admin.FeeClassMap;
import lombok.Data;

import java.util.List;

@Data
public class DiscountClassMapWrapper {
    private List<DiscountClassMap> discountClassMaps;
}
