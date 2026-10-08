package com.smsweb.sms.dto.tc;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/** The timeline rows of one academic session. */
@Getter
@AllArgsConstructor
public class TimelineSessionDto {

    private final String sessionName;
    private final List<TimelineRowDto> rows;
}
