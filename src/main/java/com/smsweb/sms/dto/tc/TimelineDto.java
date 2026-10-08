package com.smsweb.sms.dto.tc;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** Everything the timeline page shows. */
@Getter
@Setter
public class TimelineDto {

    private String studentName;
    private String fatherName;
    private String registrationNo;
    /** Records linked to this one by re-registration, oldest first. One entry when there is no link. */
    private List<TimelineChainRow> chain = new ArrayList<>();
    /** Rows grouped by session, oldest session first. */
    private List<TimelineSessionDto> sessions = new ArrayList<>();
    private int totalRows;
}
