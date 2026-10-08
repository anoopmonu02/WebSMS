package com.smsweb.sms.dto.tc;

import lombok.Getter;
import lombok.Setter;

/** Input of the re-issue dialog. The reason is mandatory (at least 10 characters). */
@Getter
@Setter
public class ReissueRequest {

    private String reason;
    private String collectedByName;
    private String collectedByRelation;
}
