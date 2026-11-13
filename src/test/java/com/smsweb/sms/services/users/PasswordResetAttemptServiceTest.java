package com.smsweb.sms.services.users;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordResetAttemptServiceTest {

    @Test
    void threeRequestsPerEmailThenLocked() {
        PasswordResetAttemptService s = new PasswordResetAttemptService();
        for (int i = 0; i < 3; i++) {
            assertFalse(s.isLocked(" A@Example.com "));
            s.recordAttempt("a@example.com");
        }
        assertTrue(s.isLocked("a@EXAMPLE.com"));
        assertFalse(s.isLocked("b@example.com"));
        assertFalse(s.isLocked(null));
    }
}
