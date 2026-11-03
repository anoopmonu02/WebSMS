package com.smsweb.sms.services.automation;

import com.smsweb.sms.models.admin.SystemConfig;
import com.smsweb.sms.repositories.admin.SystemConfigRepository;
import com.smsweb.sms.services.security.SecretCipher;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BackupNoticeServiceTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 2, 2, 0);

    private final Map<String, SystemConfig> db = new LinkedHashMap<>();
    private final List<String> sent = new ArrayList<>();      // "receivers|subject"
    private final List<String> bodies = new ArrayList<>();
    private final AutomationSettingsService settings;
    private final BackupNoticeService notices;

    /** Never talks to a mail server: records the message, or fails for one particular receiver. */
    private class FakeMail extends AutomationMailService {
        FakeMail(SecretCipher cipher) {
            super(cipher);
        }

        @Override
        public void send(AutomationSettings account, List<String> to, String subject, String body, boolean html, File attachment) throws Exception {
            if (to.contains("bad@fail.com")) {
                throw new IllegalStateException("smtp down");
            }
            sent.add(String.join(",", to) + "|" + subject);
            bodies.add(body);
        }
    }

    BackupNoticeServiceTest() {
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
        new Random(11).nextBytes(key);
        SecretCipher cipher = new SecretCipher(Base64.getEncoder().encodeToString(key));
        settings = new AutomationSettingsService(repo, cipher);
        notices = new BackupNoticeService(settings, new FakeMail(cipher));
    }

    private void sharedAccount() {
        AutomationSettingsService.AccountRequest r = new AutomationSettingsService.AccountRequest();
        r.sender = "a@b.com"; r.password = "pw"; r.fromName = ""; r.host = "smtp.gmail.com";
        r.port = 587; r.security = "STARTTLS"; r.auth = true; r.timeoutSeconds = 30;
        assertNull(settings.saveAccount(1L, true, r));
    }

    private void noticeOn(long school, String to) {
        AutomationSettingsService.BackupNoticeRequest r = new AutomationSettingsService.BackupNoticeRequest();
        r.on = true;
        r.recipients = to;
        assertNull(settings.saveBackupNotice(school, r));
    }

    private void put(String key, String value) {
        SystemConfig c = new SystemConfig();
        c.setConfigName(key);
        c.setConfigValue(value);
        db.put(key, c);
    }

    @Test
    void successNoticeGoesOnlyToBranchesThatSwitchedItOn() {
        sharedAccount();
        noticeOn(1L, "p1@s.com");
        notices.sendAll(new BackupNoticeService.Outcome(true, true, "12 MB", AT));     // school 2 has no notice row
        assertEquals(List.of("p1@s.com|Database backup completed"), sent);
        assertTrue(bodies.get(0).contains("12 MB"));
        assertFalse(bodies.get(0).contains("@"));                    // no addresses and no data in the notice
        assertTrue(db.containsKey("AUTO_SENT_BACKUP_NOTICE_1"));
    }

    @Test
    void failureNoticeIsSentOncePerDayEvenWhenTheBackupIsRetried() {
        sharedAccount();
        noticeOn(1L, "p1@s.com");
        BackupNoticeService.Outcome failed = new BackupNoticeService.Outcome(false, false, "", AT);
        notices.sendAll(failed);
        notices.sendAll(failed);                                      // the retry stays quiet
        assertEquals(1, sent.size());
        assertEquals("p1@s.com|Database backup FAILED", sent.get(0));
        notices.sendAll(new BackupNoticeService.Outcome(true, true, "5 MB", AT));      // a success the same day is still reported
        assertEquals(2, sent.size());
        notices.sendAll(new BackupNoticeService.Outcome(false, false, "", AT.plusDays(1)));   // a new day, a new failure notice
        assertEquals(3, sent.size());
    }

    @Test
    void oneBranchFailingDoesNotStopTheOthers() {
        sharedAccount();
        noticeOn(1L, "bad@fail.com");
        noticeOn(2L, "ok@s.com");
        notices.sendAll(new BackupNoticeService.Outcome(true, true, "5 MB", AT));
        assertEquals(List.of("ok@s.com|Database backup completed"), sent);
    }

    @Test
    void aBranchWithoutAWorkingAccountIsSkipped() {
        put("BACKUP_NOTICE_ON_3", "true");
        put("BACKUP_NOTICE_TO_3", "x@y.com");
        notices.sendAll(new BackupNoticeService.Outcome(true, true, "5 MB", AT));
        assertTrue(sent.isEmpty());
    }

    @Test
    void branchesAreFoundFromSwitchedOnKeysOnly() {
        Map<String, String> cfg = new HashMap<>();
        cfg.put("BACKUP_NOTICE_ON_4", "true");
        cfg.put("BACKUP_NOTICE_ON_5", "false");
        cfg.put("BACKUP_NOTICE_ON_x", "true");
        cfg.put("ATT_REMINDER_ON_6", "true");
        assertEquals(Set.of(4L), BackupNoticeService.branchesWithNotice(cfg));
    }

    @Test
    void outcomeIsReadFromTheBackupResult() {
        Map<String, Object> error = new HashMap<>();
        error.put("error", "Backup failed. secret details");
        assertFalse(BackupNoticeService.Outcome.from(error, AT).success);
        assertFalse(BackupNoticeService.Outcome.from(null, AT).success);
        Map<String, Object> ok = new HashMap<>();
        ok.put("success", true);
        ok.put("emailed", true);
        ok.put("size", "5 MB");
        BackupNoticeService.Outcome o = BackupNoticeService.Outcome.from(ok, AT);
        assertTrue(o.success);
        assertTrue(o.emailed);
        assertEquals("5 MB", o.size);
    }

    @Test
    void noticeTextsNeverRepeatTheRawErrorOrTheFile() {
        String failed = BackupNoticeService.body(new BackupNoticeService.Outcome(false, false, "", AT));
        assertTrue(failed.contains("FAILED"));
        assertTrue(failed.contains("try again"));
        String kept = BackupNoticeService.body(new BackupNoticeService.Outcome(true, false, "5 MB", AT));
        assertTrue(kept.contains("saved on the server"));
        assertEquals("Database backup completed", BackupNoticeService.subject(new BackupNoticeService.Outcome(true, true, "", AT)));
    }
}
