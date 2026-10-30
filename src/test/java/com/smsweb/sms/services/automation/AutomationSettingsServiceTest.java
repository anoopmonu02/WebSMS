package com.smsweb.sms.services.automation;

import com.smsweb.sms.models.admin.SystemConfig;
import com.smsweb.sms.repositories.admin.SystemConfigRepository;
import com.smsweb.sms.services.security.SecretCipher;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class AutomationSettingsServiceTest {

    private final Map<String, SystemConfig> db = new LinkedHashMap<>();
    private final AutomationSettingsService service;
    private final BackupScheduleService backup;

    AutomationSettingsServiceTest() {
        SystemConfigRepository repo = (SystemConfigRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{SystemConfigRepository.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "findAll": return new ArrayList<>(db.values());
                        case "findByConfigName": return Optional.ofNullable(db.get((String) a[0]));
                        case "save": SystemConfig c = (SystemConfig) a[0]; db.put(c.getConfigName(), c); return c;
                        case "delete": db.remove(((SystemConfig) a[0]).getConfigName()); return null;
                        default: throw new UnsupportedOperationException(m.getName());
                    }
                });
        byte[] key = new byte[32];
        new Random(7).nextBytes(key);
        SecretCipher cipher = new SecretCipher(Base64.getEncoder().encodeToString(key));
        service = new AutomationSettingsService(repo, cipher);
        backup = new BackupScheduleService(repo, service);
    }

    private static AutomationSettingsService.AccountRequest account() {
        AutomationSettingsService.AccountRequest r = new AutomationSettingsService.AccountRequest();
        r.sender = "a@b.com"; r.password = "secret pass"; r.fromName = "My School"; r.host = "smtp.gmail.com";
        r.port = 587; r.security = "STARTTLS"; r.auth = true; r.timeoutSeconds = 30;
        return r;
    }

    private static AutomationSettingsService.ReminderRequest reminder(boolean on, String to) {
        AutomationSettingsService.ReminderRequest r = new AutomationSettingsService.ReminderRequest();
        r.on = on; r.time = "11:00"; r.gapMinutes = 30; r.recipients = to;
        return r;
    }

    @Test
    void accountIsSavedWithEncryptedPassword() {
        assertNull(service.saveAccount(5L, account()));
        assertTrue(db.get("AUTO_MAIL_PASSWORD_5").getConfigValue().startsWith("ENC:v1:"));
        AutomationSettings s = service.load(5L);
        assertTrue(s.isAccountReady());
        assertEquals("My School", s.getMailFromName());
        assertEquals(587, s.getMailPort());
    }

    @Test
    void accountValidation() {
        AutomationSettingsService.AccountRequest r = account();
        r.sender = "bad";
        assertNotNull(service.saveAccount(5L, r));
        r = account(); r.host = "bad host!";
        assertNotNull(service.saveAccount(5L, r));
        r = account(); r.port = 70000;
        assertNotNull(service.saveAccount(5L, r));
        r = account(); r.security = "WHAT";
        assertNotNull(service.saveAccount(5L, r));
        r = account(); r.fromName = "a\nb";
        assertNotNull(service.saveAccount(5L, r));
        r = account(); r.password = ""; // login required but no password saved yet
        assertNotNull(service.saveAccount(5L, r));
        assertTrue(db.isEmpty());
    }

    @Test
    void accountWithoutLoginNeedsNoPassword() {
        AutomationSettingsService.AccountRequest r = account();
        r.auth = false; r.password = ""; r.security = "NONE"; r.port = 25;
        assertNull(service.saveAccount(5L, r));
        assertTrue(service.load(5L).isAccountReady());
    }

    @Test
    void blankPasswordKeepsTheSavedOne() {
        service.saveAccount(5L, account());
        String before = db.get("AUTO_MAIL_PASSWORD_5").getConfigValue();
        AutomationSettingsService.AccountRequest r = account();
        r.password = ""; r.port = 465; r.security = "SSL";
        assertNull(service.saveAccount(5L, r));
        assertEquals(before, db.get("AUTO_MAIL_PASSWORD_5").getConfigValue());
        assertEquals("SSL", service.load(5L).getMailSecurity());
    }

    @Test
    void notificationCannotBeSwitchedOnWithoutAccountOrReceiver() {
        assertNotNull(service.saveReminder(5L, reminder(true, "x@y.com")));   // no account yet
        service.saveAccount(5L, account());
        assertNotNull(service.saveReminder(5L, reminder(true, "")));          // no receiver
        assertNull(service.saveReminder(5L, reminder(true, "x@y.com, z@y.com")));
        assertEquals(2, service.load(5L).getReminderRecipients().size());
        assertTrue(service.load(5L).isReminderOn());
    }

    @Test
    void receiverValidation() {
        assertNull(AutomationSettingsService.validateRecipients(""));
        assertNull(AutomationSettingsService.validateRecipients("a@b.com, c@d.com"));
        assertNotNull(AutomationSettingsService.validateRecipients("a@b.com, nope"));
        assertNotNull(AutomationSettingsService.validateRecipients("a@b.com b@b.com c@b.com d@b.com e@b.com f@b.com"));
    }

    @Test
    void reportSettingsRoundTrip() {
        service.saveAccount(5L, account());
        AutomationSettingsService.ReportRequest r = new AutomationSettingsService.ReportRequest();
        r.on = true; r.days = 7; r.frequency = "MONTHLY"; r.time = "08:00"; r.dayOfWeek = "MON"; r.dayOfMonth = "LAST"; r.recipients = "p@s.com";
        assertNull(service.saveReport(5L, r));
        AutomationSettings s = service.load(5L);
        assertEquals(AutomationScheduleUtil.Frequency.MONTHLY, s.getReportFrequency());
        assertEquals("LAST", s.getReportDayOfMonth());
        r.days = 99;
        assertNotNull(service.saveReport(5L, r));
    }

    @Test
    void settingsOfOneSchoolDoNotLeakToAnother() {
        service.saveAccount(5L, account());
        assertFalse(service.load(6L).isAccountReady());
    }

    @Test
    void backupScheduleSaveAndRead() {
        assertNotNull(backup.save(5L, "DAILY", null, "02:00", "02:05", "a@b.com"));   // too close
        assertNotNull(backup.save(5L, "DAILY", null, "02:00", "", "bad"));            // bad receiver
        assertNull(backup.save(5L, "DAILY", null, "02:00", "14:00", "a@b.com"));
        Map<String, Object> m = backup.get(5L);
        assertEquals("DAILY", m.get("frequency"));
        assertEquals("14:00", m.get("time2"));
        assertEquals("a@b.com", m.get("receivers"));
        assertEquals("5", db.get("DB_BACKUP_MAIL_SCHOOL_ID").getConfigValue());
        assertNull(backup.save(5L, "WEEKLY", "SUN", "03:15", "", ""));
        assertEquals("0 15 3 * * SUN", db.get("DB_BACKUP_SCHEDULE").getConfigValue());
        assertFalse(db.containsKey("DB_BACKUP_TIME_2"));
    }
}
