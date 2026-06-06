package com.smsweb.sms.dto.mobile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Request body for POST /api/v1/auth/login
 *
 * Parent logs in with mobile number + password.
 * Example: { "mobile": "9876543210", "password": "abc123" }
 */
@Data
public class MobileLoginRequest {

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be 10 digits")
    private String mobile;

    @NotBlank(message = "Password is required")
    private String password;
}
