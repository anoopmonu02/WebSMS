package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.models.admin.FeeClassMap;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FeeClassMapWrapper {
    private List<FeeClassMap> feeClassMaps;
}
