package com.smsweb.sms.services.mobile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptServiceTest {

    @Test
    void fiveFailuresLockAndSuccessClears() {
        LoginAttemptService s = new LoginAttemptService();
        for (int i = 0; i < 4; i++) s.recordFailure("9876543210");
        assertFalse(s.isLocked("9876543210"));
        s.recordFailure("9876543210");
        assertTrue(s.isLocked("9876543210"));
        assertTrue(s.remainingLockoutMinutes("9876543210") >= 14);
        assertFalse(s.isLocked("1111111111"));
        s.recordSuccess("9876543210");
        assertFalse(s.isLocked("9876543210"));
        assertEquals(0, s.remainingLockoutMinutes("9876543210"));
    }
}
