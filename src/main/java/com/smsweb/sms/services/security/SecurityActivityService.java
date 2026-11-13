package com.smsweb.sms.services.security;

import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.repositories.employee.EmployeeRepository;
import com.smsweb.sms.repositories.users.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Builds the "Security activity" card of App Config: counts and a short list of the last 24 hours, with masked names
 * only. A staff sign-in lock is shown only to the branch the account belongs to; events that belong to no branch
 * (an address, a parent number, the API) are shown to every admin. Read-only; never throws to the caller.
 */
@Service
public class SecurityActivityService {

    private static final Logger log = LoggerFactory.getLogger(SecurityActivityService.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM, HH:mm");
    static final int LIST_SIZE = 20;

    private final SecurityActivityLog activity;
    private final ThrottleSettings settings;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;

    public SecurityActivityService(SecurityActivityLog activity, ThrottleSettings settings,
                                   UserRepository userRepository, EmployeeRepository employeeRepository) {
        this.activity = activity;
        this.settings = settings;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
    }

    public Map<String, Object> view(Long schoolId) {
        Map<String, Long> cache = new HashMap<>();
        Function<String, Long> branchOf = key -> cache.computeIfAbsent(key, this::branchOfStaff);
        return build(activity.recent(), schoolId, branchOf, settings.mode(), ZoneId.systemDefault());
    }

    /** The branch id of a staff username, or null when it is unknown, not a staff account, or has no branch. */
    private Long branchOfStaff(String usernameKey) {
        try {
            UserEntity user = userRepository.findByUsername(usernameKey);
            if (user == null) {
                return null;
            }
            Employee employee = employeeRepository.findByUserEntity(user);
            return employee == null || employee.getSchool() == null ? null : employee.getSchool().getId();
        } catch (RuntimeException e) {
            log.warn("Could not find the branch of an account for the security activity list: {}", e.getMessage());
            return null;
        }
    }

    static Map<String, Object> build(List<SecurityActivityLog.Event> all, Long schoolId, Function<String, Long> branchOf,
                                     String mode, ZoneId zone) {
        List<SecurityActivityLog.Event> visible = new ArrayList<>();
        for (SecurityActivityLog.Event e : all) {
            if (e.staffKey() != null) {
                Long branch = branchOf.apply(e.staffKey());
                if (branch != null && schoolId != null && !branch.equals(schoolId)) {
                    continue; // another branch's staff account
                }
            }
            visible.add(e);
        }
        int locked = 0;
        int ipBlocks = 0;
        int apiHeld = 0;
        for (SecurityActivityLog.Event e : visible) {
            switch (e.type()) {
                case ACCOUNT_LOCK, PARENT_LOCK -> locked++;
                case IP_BLOCK -> ipBlocks++;
                case API_HELD -> apiHeld++;
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SecurityActivityLog.Event e : visible) {
            if (rows.size() >= LIST_SIZE) {
                break;
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("time", WHEN.format(Instant.ofEpochMilli(e.at()).atZone(zone)));
            r.put("who", e.who() == null ? "-" : e.who());
            r.put("what", text(e));
            r.put("level", e.level());
            rows.add(r);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        m.put("mode", mode);
        m.put("lockedSignIns", locked);
        m.put("addressBlocks", ipBlocks);
        m.put("apiHeld", apiHeld);
        m.put("events", rows);
        return m;
    }

    static String text(SecurityActivityLog.Event e) {
        String suffix = e.enforced() ? "" : " (log mode: not really locked)";
        return switch (e.type()) {
            case ACCOUNT_LOCK -> "Staff sign-in locked " + e.lockMinutes() + " min" + suffix;
            case PARENT_LOCK -> "Parent sign-in locked " + e.lockMinutes() + " min";
            case IP_BLOCK -> (e.accounts() > 1
                    ? "Many accounts tried (" + e.accounts() + "), address blocked " + e.lockMinutes() + " min"
                    : "Too many wrong attempts, address blocked " + e.lockMinutes() + " min") + suffix;
            case API_HELD -> "Too many app requests for minutes in a row, held " + e.lockMinutes() + " min" + suffix;
        };
    }
}
