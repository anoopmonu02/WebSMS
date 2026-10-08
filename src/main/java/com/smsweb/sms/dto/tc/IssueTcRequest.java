package com.smsweb.sms.dto.tc;

import lombok.Getter;
import lombok.Setter;

/** Input of the "Issue TC" dialog: who collected the TC. */
@Getter
@Setter
public class IssueTcRequest {

    private String collectedByName;
    /** PARENT, GUARDIAN or STUDENT. */
    private String collectedByRelation;
}
