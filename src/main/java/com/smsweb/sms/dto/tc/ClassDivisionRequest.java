package com.smsweb.sms.dto.tc;

import lombok.Getter;
import lombok.Setter;

/** Fields of the inline add / edit row on the Class Groups page. */
@Getter
@Setter
public class ClassDivisionRequest {
    private String name;
    private String description;
    private String tcFormat;
    private Long startNumber;
    /** Active or Inactive. */
    private String status;
}
