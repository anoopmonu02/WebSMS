package com.smsweb.sms.dto.mobile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for POST /api/v1/auth/select-child
 */
@Data
public class SelectChildRequest {

    @NotBlank(message = "Temp token is required")
    private String tempToken;

    @NotNull(message = "Academic student ID is required")
    private Long academicStudentId;
}
