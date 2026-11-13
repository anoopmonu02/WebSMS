package com.smsweb.sms.services.automation;

import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.repositories.employee.EmployeeRepository;
import com.smsweb.sms.repositories.users.UserRepository;
import com.smsweb.sms.services.security.LoginThrottleService;
import com.smsweb.sms.services.security.Masking;
import com.smsweb.sms.services.security.SecurityEventSink;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NEW service (phase 2 of login/API throttling). Emails a branch's "Security alert" receivers when
 * <ul>
 *   <li>a STAFF account is locked for the second time (or later) within 24 hours, or</li>
 *   <li>an IP address is blocked for failing on many different accounts (password spraying).</li>
 * </ul>
 * The first lock is never emailed (an ordinary typo), parents are never emailed, and an unknown username is never
 * emailed (so an attacker cannot make the system send mail by typing random names).
 *
 * <p>Safe by design, like {@link BackupNoticeService}: runs on its own thread, so a slow mail server never delays a
 * sign-in; never throws; one branch failing never affects another; at most one email per account per level per day
 * and one per address per day. The text carries a masked username, the time, the address and the lock end, never a
 * password or a guess.</p>
 */
@Service
public class SecurityAlertService implements SecurityEventSink {

    private static final Logger log = LoggerFactory.getLogger(SecurityAlertService.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private static final int MIN_ALERT_LEVEL = 2;
    private static final int MAX_REMEMBERED = 2000;

    private final AutomationSettingsService settingsService;
    private final AutomationMailService mailService;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final LoginThrottleService throttle;

    private final Set<String> sentToday = new HashSet<>();
    private LocalDate sentDay = LocalDate.now();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "security-alert");
        t.setDaemon(true);
        return t;
    });

    public SecurityAlertService(AutomationSettingsService settingsService, AutomationMailService mailService,
                                UserRepository userRepository, EmployeeRepository employeeRepository,
                                LoginThrottleService throttle) {
        this.settingsService = settingsService;
        this.mailService = mailService;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.throttle = throttle;
    }

    @PostConstruct
    void register() {
        throttle.setSink(this);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    @Override
    public void accountLocked(LoginThrottleService.Channel channel, String key, int level, long lockMinutes, String ip) {
        if (channel != LoginThrottleService.Channel.WEB || level < MIN_ALERT_LEVEL || key == null) {
            return; // staff web sign-in only; the first lock is just a typo
        }
        queue(() -> sendAccountAlert(key, level, lockMinutes, ip, LocalDateTime.now()));
    }

    @Override
    public void ipBlocked(LoginThrottleService.Channel channel, String ip, int level, long lockMinutes,
                          int differentAccounts, boolean spray) {
        if (!spray || ip == null || channel != LoginThrottleService.Channel.WEB) {
            return;
        }
        queue(() -> sendSprayAlert(ip, lockMinutes, differentAccounts, LocalDateTime.now()));
    }

    private void queue(Runnable work) {
        try {
            executor.submit(() -> {
                try {
                    work.run();
                } catch (Exception e) {
                    log.error("Security alert failed", e);
                }
            });
        } catch (RuntimeException e) {
            log.warn("Could not queue the security alert: {}", e.getMessage());
        }
    }

    // ── sending ─────────────────────────────────────────────────────────────────────────────────

    void sendAccountAlert(String usernameKey, int level, long lockMinutes, String ip, LocalDateTime now) {
        UserEntity user = userRepository.findByUsername(usernameKey);
        if (user == null) {
            return; // unknown username: nothing to route, nothing to say
        }
        Employee employee = employeeRepository.findByUserEntity(user);
        if (employee == null) {
            return; // not a staff account (for example a student login)
        }
        if (!firstTimeToday("acct|" + usernameKey + "|" + level, now.toLocalDate())) {
            return;
        }
        Long schoolId = employee.getSchool() == null ? null : employee.getSchool().getId();
        String masked = Masking.username(usernameKey);
        String body = accountBody(masked, level, lockMinutes, ip, now);
        sendToBranches(schoolId, "Security alert: sign-in locked for " + masked, body);
    }

    void sendSprayAlert(String ip, long lockMinutes, int accounts, LocalDateTime now) {
        if (!firstTimeToday("ip|" + ip, now.toLocalDate())) {
            return;
        }
        sendToBranches(null, "Security alert: many failed sign-ins from one address", sprayBody(ip, lockMinutes, accounts, now));
    }

    /** schoolId null = every branch that switched the alert on (the problem is not tied to one branch). */
    private void sendToBranches(Long schoolId, String subject, String body) {
        Map<String, String> cfg = settingsService.snapshot();
        Set<Long> targets = new LinkedHashSet<>();
        if (schoolId != null) {
            targets.add(schoolId);
        } else {
            targets.addAll(branchesWithAlert(cfg));
        }
        for (Long id : targets) {
            try {
                AutomationSettings s = settingsService.fromSnapshot(cfg, id);
                List<String> to = s.getSecurityAlertRecipients();
                if (!s.isSecurityAlertOn() || to.isEmpty() || !s.isAccountReady()) {
                    continue;
                }
                mailService.send(s, to, subject, body, false);
                settingsService.markSent(AutomationSettingsService.JOB_SECURITY_ALERT, id);
            } catch (Exception e) {
                log.error("Security alert for school {} failed: {}", id, e.getMessage());
            }
        }
    }

    private synchronized boolean firstTimeToday(String key, LocalDate today) {
        if (!today.equals(sentDay) || sentToday.size() > MAX_REMEMBERED) {
            sentToday.clear();
            sentDay = today;
        }
        return sentToday.add(key);
    }

    // ── text ────────────────────────────────────────────────────────────────────────────────────

    static String accountBody(String maskedUser, int level, long lockMinutes, String ip, LocalDateTime now) {
        return "A staff sign-in was locked after repeated wrong passwords.\n\n"
                + "Account: " + maskedUser + '\n'
                + "Time: " + now.format(WHEN) + '\n'
                + "From address: " + Masking.plain(ip) + '\n'
                + "Lock number in the last 24 hours: " + level + '\n'
                + "Locked for: " + lockMinutes + " minutes (until " + now.plusMinutes(lockMinutes).format(WHEN) + ")\n\n"
                + "The lock ends by itself. If this was not the real user, reset the password from the Employee screen "
                + "(Reset Password), which also unlocks the account at once.\n\n"
                + "This is only a notice. No password or guess is included.";
    }

    static String sprayBody(String ip, long lockMinutes, int accounts, LocalDateTime now) {
        return "One address tried wrong passwords on many different accounts, which is typical of password guessing.\n\n"
                + "Time: " + now.format(WHEN) + '\n'
                + "From address: " + Masking.plain(ip) + '\n'
                + "Different accounts tried: " + accounts + '\n'
                + "Address blocked for: " + lockMinutes + " minutes (until " + now.plusMinutes(lockMinutes).format(WHEN) + ")\n\n"
                + "Nothing is needed from you. The block ends by itself. If you see this often, ask your system "
                + "administrator to check the server.\n\n"
                + "This is only a notice. No password or guess is included.";
    }

    /** Branches that switched the alert on (found from the config keys, no school query needed). */
    static Set<Long> branchesWithAlert(Map<String, String> cfg) {
        Set<Long> ids = new LinkedHashSet<>();
        String prefix = AutomationSettingsService.K_SECURITY_ALERT_ON;
        for (Map.Entry<String, String> e : cfg.entrySet()) {
            if (!e.getKey().startsWith(prefix) || !"true".equalsIgnoreCase(e.getValue())) {
                continue;
            }
            try {
                ids.add(Long.parseLong(e.getKey().substring(prefix.length())));
            } catch (NumberFormatException ignored) {
                // not one of our keys
            }
        }
        return ids;
    }
}
