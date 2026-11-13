package com.smsweb.sms.services.automation;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SecurityAlertServiceTest {

    @Test
    void textsCarryNoSecretsAndSayWhatToDo() {
        String a = SecurityAlertService.accountBody("ra***", 2, 30, "203.0.113.9", LocalDateTime.of(2026, 11, 3, 10, 0));
        assertTrue(a.contains("ra***"));
        assertTrue(a.contains("203.0.113.9"));
        assertTrue(a.contains("Reset Password"));
        assertFalse(a.toLowerCase().contains("guess:"));
        String s = SecurityAlertService.sprayBody("203.0.113.9", 30, 20, LocalDateTime.of(2026, 11, 3, 10, 0));
        assertTrue(s.contains("20"));
        assertTrue(s.contains("203.0.113.9"));
    }

    @Test
    void findsBranchesThatSwitchedTheAlertOn() {
        Map<String, String> cfg = new HashMap<>();
        cfg.put("SECURITY_ALERT_ON_1", "true");
        cfg.put("SECURITY_ALERT_ON_2", "false");
        cfg.put("SECURITY_ALERT_ON_x", "true");
        cfg.put("SECURITY_ALERT_ON_7", "TRUE");
        cfg.put("BACKUP_NOTICE_ON_3", "true");
        assertEquals(2, SecurityAlertService.branchesWithAlert(cfg).size());
        assertTrue(SecurityAlertService.branchesWithAlert(cfg).contains(7L));
    }
}
