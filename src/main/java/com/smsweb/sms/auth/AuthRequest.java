package com.smsweb.sms.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AuthRequest {

    private String username;
    private String password;

    public AuthRequest() {}

    public String getUsername() { return username; }
    public String getPassword() { return password; }

    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }
}
