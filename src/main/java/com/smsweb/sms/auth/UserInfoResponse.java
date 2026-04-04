package com.smsweb.sms.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserInfoResponse(
        Long         id,           // ✅ primitive
        String       username,     // ✅ primitive
        String       email,        // ✅ primitive
        List<String> roles,        // ✅ List of String — safe
        String       displayName,  // ✅ primitive
        String       userType,     // ✅ primitive
        Long         profileId     // ✅ primitive
) {}
