package com.smsweb.sms.services.tc;

import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.admin.SystemConfig;
import com.smsweb.sms.repositories.admin.SystemConfigRepository;
import com.smsweb.sms.repositories.universal.GradeRepository;
import com.smsweb.sms.repositories.tc.ClassDivisionRepository;
import com.smsweb.sms.repositories.tc.DueClearanceReceiptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Reads the TC settings from the existing {@code system_config} table.
 *
 * <p>{@code config_name} is the primary key of that table, so every TC setting is stored per
 * school as {@code <KEY>_<schoolId>} (the same way {@code SCHOOL_PSRN_<id>} works). A missing
 * key means the default below; nothing has to be seeded. With no keys at all, the whole feature
 * is OFF and the application behaves exactly as before.</p>
 *
 * <p>Values are cached for a short time because the sidebar will ask {@link #isEnabled} on every
 * page. Writers (added with the TC Settings tab) must call {@link #evict(String)} after saving.
 * On any database error the default is returned (feature off) and nothing is cached.</p>
 */
@Service
public class TcConfigService {

    private static final Logger log = LoggerFactory.getLogger(TcConfigService.class);

    // Key families, stored as <KEY>_<schoolId>
    public static final String KEY_FEATURE_ENABLED = "TC_FEATURE_ENABLED";
    public static final String KEY_ABSENCE_ALERT_DAYS = "TC_ABSENCE_ALERT_DAYS";
    public static final String KEY_ADMISSION_RULE_ENABLED = "TC_ADMISSION_RULE_ENABLED";
    public static final String KEY_RULE_EXEMPT_GRADE_IDS = "TC_RULE_EXEMPT_GRADE_IDS";
    public static final String KEY_RULE_NOTARY_GRADE_IDS = "TC_RULE_NOTARY_GRADE_IDS";
    public static final String KEY_RULE_MANDATORY_GRADE_IDS = "TC_RULE_MANDATORY_GRADE_IDS";
    public static final String KEY_FORMAT_NAMES = "TC_FORMAT_NAMES";
    public static final String KEY_RECEIPT_START = "SCHOOL_DCR";
    public static final String KEY_RECEIPT_PREFIX = "SCHOOL_DCR_PREFIX";
    public static final String KEY_RECEIPT_PAD = "SCHOOL_DCR_PAD";

    // Defaults when a key is missing or invalid
    public static final int DEFAULT_ABSENCE_ALERT_DAYS = 60;
    public static final int MAX_ABSENCE_ALERT_DAYS = 366;
    public static final List<String> DEFAULT_FORMAT_NAMES = List.of("BASIC_EDUCATION", "BOARD_HS_INTER");
    public static final long DEFAULT_RECEIPT_START = 1L;
    public static final String DEFAULT_RECEIPT_PREFIX = "DCR-";
    public static final int DEFAULT_RECEIPT_PAD = 5;
    public static final int MAX_RECEIPT_PREFIX_LENGTH = 10;
    public static final int MAX_RECEIPT_PAD = 10;

    private static final Pattern FORMAT_NAME = Pattern.compile("[A-Z0-9_]+");
    private static final long CACHE_TTL_MILLIS = 60_000L;

    private static final Pattern RECEIPT_PREFIX = Pattern.compile("[A-Za-z0-9/_-]{1,10}");
    private static final int MAX_FORMAT_NAMES = 20;
    private static final int MAX_FORMAT_NAME_LENGTH = 30;
    private static final long MAX_RECEIPT_START = 9_999_999_999L;

    private final SystemConfigRepository systemConfigRepository;
    private final DueClearanceReceiptRepository dueClearanceReceiptRepository;
    private final ClassDivisionRepository classDivisionRepository;
    private final GradeRepository gradeRepository;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public TcConfigService(SystemConfigRepository systemConfigRepository,
                           DueClearanceReceiptRepository dueClearanceReceiptRepository,
                           ClassDivisionRepository classDivisionRepository,
                           GradeRepository gradeRepository) {
        this.systemConfigRepository = systemConfigRepository;
        this.dueClearanceReceiptRepository = dueClearanceReceiptRepository;
        this.classDivisionRepository = classDivisionRepository;
        this.gradeRepository = gradeRepository;
    }

    // ── Public readers ───────────────────────────────────────────────────────

    /**
     * Same as {@link #isEnabled(School)} but with a single signature and null-safe. Templates
     * (the sidebar in base.html) call this one, because an expression that passes null to an
     * overloaded method can be ambiguous.
     */
    public boolean featureEnabled(School school) {
        return isEnabled(idOf(school));
    }

    /** Master switch for the school. Default false. */
    public boolean isEnabled(School school) {
        return isEnabled(idOf(school));
    }

    public boolean isEnabled(Long schoolId) {
        return readBoolean(KEY_FEATURE_ENABLED, schoolId, false);
    }

    /** Long-absence threshold in total absent days. Default 60. */
    public int absenceThreshold(School school) {
        int value = (int) readLong(KEY_ABSENCE_ALERT_DAYS, idOf(school), DEFAULT_ABSENCE_ALERT_DAYS);
        return value >= 1 ? value : DEFAULT_ABSENCE_ALERT_DAYS;
    }

    /** Turns the registration-form admission rule on. Default false. */
    public boolean admissionRuleEnabled(School school) {
        return readBoolean(KEY_ADMISSION_RULE_ENABLED, idOf(school), false);
    }

    /** Grades up to class 5: admit directly, remark mandatory. */
    public Set<Long> exemptGradeIds(School school) {
        return readIdSet(KEY_RULE_EXEMPT_GRADE_IDS, idOf(school));
    }

    /** Class 6: TC or notary affidavit. */
    public Set<Long> notaryGradeIds(School school) {
        return readIdSet(KEY_RULE_NOTARY_GRADE_IDS, idOf(school));
    }

    /** Class 7 and above: TC required. Grades not listed anywhere are also treated as mandatory. */
    public Set<Long> mandatoryGradeIds(School school) {
        return readIdSet(KEY_RULE_MANDATORY_GRADE_IDS, idOf(school));
    }

    /** TC format names offered on the class group form. Default BASIC_EDUCATION, BOARD_HS_INTER. */
    public List<String> formatNames(School school) {
        Optional<String> raw = read(KEY_FORMAT_NAMES, idOf(school));
        if (raw.isEmpty()) {
            return DEFAULT_FORMAT_NAMES;
        }
        Set<String> names = new LinkedHashSet<>();
        for (String part : raw.get().split(",")) {
            String name = part.trim().toUpperCase();
            if (FORMAT_NAME.matcher(name).matches()) {
                names.add(name);
            } else if (!name.isEmpty()) {
                log.warn("Ignoring invalid TC format name '{}' in {}", part.trim(), key(KEY_FORMAT_NAMES, idOf(school)));
            }
        }
        return names.isEmpty() ? DEFAULT_FORMAT_NAMES : Collections.unmodifiableList(new ArrayList<>(names));
    }

    /** Receipt number start value (used only for the school's first receipt). Default 1. */
    public long receiptStart(School school) {
        long value = readLong(KEY_RECEIPT_START, idOf(school), DEFAULT_RECEIPT_START);
        return value >= 1 ? value : DEFAULT_RECEIPT_START;
    }

    /** Receipt number prefix, up to 10 characters. Default "DCR-". */
    public String receiptPrefix(School school) {
        Optional<String> raw = read(KEY_RECEIPT_PREFIX, idOf(school));
        if (raw.isEmpty() || raw.get().isBlank()) {
            return DEFAULT_RECEIPT_PREFIX;
        }
        String value = raw.get().trim();
        return value.length() <= MAX_RECEIPT_PREFIX_LENGTH ? value : DEFAULT_RECEIPT_PREFIX;
    }

    /** Receipt number zero padding, 1 to 10. Default 5. */
    public int receiptPad(School school) {
        int value = (int) readLong(KEY_RECEIPT_PAD, idOf(school), DEFAULT_RECEIPT_PAD);
        return value >= 1 && value <= MAX_RECEIPT_PAD ? value : DEFAULT_RECEIPT_PAD;
    }

    /** true once the school has any Due Clearance Receipt (the receipt start number is then locked). */
    public boolean receiptStartLocked(School school) {
        Long schoolId = idOf(school);
        return schoolId != null && dueClearanceReceiptRepository.existsBySchool_Id(schoolId);
    }

    // ── Writers (TC Settings tab). Each validates, writes <KEY>_<schoolId>, then evicts the cache ──

    /** Turns the whole TC feature on or off for the school. */
    public void setEnabled(School school, boolean enabled) {
        Long schoolId = requireSchoolId(school);
        write(KEY_FEATURE_ENABLED, schoolId, String.valueOf(enabled),
                "Master switch for the Transfer Certificate feature (per school)");
    }

    /**
     * Saves the TC format names offered on the class group form. A name that a class group
     * already uses cannot be removed.
     *
     * @throws TcValidationException with a readable message when the input is not valid
     */
    public void setFormatNames(School school, List<String> names) {
        Long schoolId = requireSchoolId(school);
        Set<String> clean = new LinkedHashSet<>();
        if (names != null) {
            for (String raw : names) {
                String name = raw == null ? "" : raw.trim().toUpperCase();
                if (name.isEmpty()) {
                    continue;
                }
                if (name.length() > MAX_FORMAT_NAME_LENGTH || !FORMAT_NAME.matcher(name).matches()) {
                    throw new TcValidationException("Format name '" + raw.trim()
                            + "' is not valid. Use capital letters, digits and underscore only (up to "
                            + MAX_FORMAT_NAME_LENGTH + " characters).");
                }
                clean.add(name);
            }
        }
        if (clean.isEmpty()) {
            throw new TcValidationException("Add at least one TC format name.");
        }
        if (clean.size() > MAX_FORMAT_NAMES) {
            throw new TcValidationException("Too many format names (maximum " + MAX_FORMAT_NAMES + ").");
        }
        for (String existing : formatNames(school)) {
            if (!clean.contains(existing)
                    && classDivisionRepository.existsBySchool_IdAndTcFormat(schoolId, existing)) {
                throw new TcValidationException("Format '" + existing
                        + "' is used by a class group, so it cannot be removed.");
            }
        }
        String joined = String.join(",", clean);
        if (joined.length() > 255) {
            throw new TcValidationException("The list of format names is too long (maximum 255 characters in total).");
        }
        write(KEY_FORMAT_NAMES, schoolId, joined, "TC format names offered on the class group form (per school)");
    }

    /**
     * Saves the receipt number prefix, zero padding and start value. The start value cannot be
     * changed once a receipt exists (it is only used for the school's first receipt).
     *
     * @throws TcValidationException with a readable message when the input is not valid
     */
    public void setReceiptNumbering(School school, String prefix, int pad, long start) {
        Long schoolId = requireSchoolId(school);
        String cleanPrefix = prefix == null ? "" : prefix.trim();
        if (!RECEIPT_PREFIX.matcher(cleanPrefix).matches()) {
            throw new TcValidationException("Prefix must be 1 to " + MAX_RECEIPT_PREFIX_LENGTH
                    + " characters: letters, digits, dash, slash or underscore.");
        }
        if (pad < 1 || pad > MAX_RECEIPT_PAD) {
            throw new TcValidationException("Padding must be between 1 and " + MAX_RECEIPT_PAD + ".");
        }
        if (start < 1 || start > MAX_RECEIPT_START) {
            throw new TcValidationException("Start number must be between 1 and " + MAX_RECEIPT_START + ".");
        }
        if (start != receiptStart(school) && receiptStartLocked(school)) {
            throw new TcValidationException("The start number cannot be changed after the first receipt is created.");
        }
        write(KEY_RECEIPT_PREFIX, schoolId, cleanPrefix, "Due Clearance Receipt number prefix (per school)");
        write(KEY_RECEIPT_PAD, schoolId, String.valueOf(pad), "Due Clearance Receipt number zero padding (per school)");
        write(KEY_RECEIPT_START, schoolId, String.valueOf(start),
                "Due Clearance Receipt number start value, used for the first receipt only (per school)");
    }

    /**
     * Saves the long-absence threshold (total absent days in the session).
     *
     * @throws TcValidationException when the number is outside 1 to {@link #MAX_ABSENCE_ALERT_DAYS}
     */
    public void setAbsenceThreshold(School school, int days) {
        Long schoolId = requireSchoolId(school);
        if (days < 1 || days > MAX_ABSENCE_ALERT_DAYS) {
            throw new TcValidationException("Absence alert days must be between 1 and " + MAX_ABSENCE_ALERT_DAYS + ".");
        }
        write(KEY_ABSENCE_ALERT_DAYS, schoolId, String.valueOf(days),
                "Long-absence alert threshold in total absent days (per school)");
    }

    /**
     * Saves the registration-form admission rule: the switch and the three grade lists. A grade may be in
     * only one list, and every id must be an existing grade.
     *
     * @throws TcValidationException with a readable message when the input is not valid
     */
    public void setAdmissionRule(School school, boolean enabled,
                                 Set<Long> exemptGradeIds, Set<Long> notaryGradeIds, Set<Long> mandatoryGradeIds) {
        Long schoolId = requireSchoolId(school);
        Set<Long> exempt = cleanIds(exemptGradeIds);
        Set<Long> notary = cleanIds(notaryGradeIds);
        Set<Long> mandatory = cleanIds(mandatoryGradeIds);

        Set<Long> seen = new LinkedHashSet<>();
        for (Set<Long> list : List.of(exempt, notary, mandatory)) {
            for (Long id : list) {
                if (!seen.add(id)) {
                    throw new TcValidationException("A grade can be in only one of the three lists.");
                }
            }
        }
        if (!seen.isEmpty()) {
            Set<Long> known = new LinkedHashSet<>();
            gradeRepository.findAllById(seen).forEach(g -> known.add(g.getId()));
            if (!known.containsAll(seen)) {
                throw new TcValidationException("One of the grades no longer exists. Please reload the page and try again.");
            }
        }
        String exemptCsv = joinIds(exempt);
        String notaryCsv = joinIds(notary);
        String mandatoryCsv = joinIds(mandatory);
        if (exemptCsv.length() > 255 || notaryCsv.length() > 255 || mandatoryCsv.length() > 255) {
            throw new TcValidationException("Too many grades in one list.");
        }
        write(KEY_RULE_EXEMPT_GRADE_IDS, schoolId, exemptCsv, "Grades admitted without a TC, remark mandatory (per school)");
        write(KEY_RULE_NOTARY_GRADE_IDS, schoolId, notaryCsv, "Grades admitted with a TC or a notary affidavit (per school)");
        write(KEY_RULE_MANDATORY_GRADE_IDS, schoolId, mandatoryCsv, "Grades that need a TC at admission (per school)");
        write(KEY_ADMISSION_RULE_ENABLED, schoolId, String.valueOf(enabled),
                "Turns the admission TC rule on the registration form on or off (per school)");
    }

    // ── Helpers for writers ──────────────────────────────────────────────────

    private static Set<Long> cleanIds(Set<Long> ids) {
        Set<Long> clean = new LinkedHashSet<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null && id > 0) {
                    clean.add(id);
                }
            }
        }
        return clean;
    }

    private static String joinIds(Set<Long> ids) {
        List<String> parts = new ArrayList<>();
        for (Long id : ids) {
            parts.add(String.valueOf(id));
        }
        return String.join(",", parts);
    }


    /** Stored config name for a key family and school, for example TC_FEATURE_ENABLED_2. */
    public static String key(String keyFamily, Long schoolId) {
        return keyFamily + "_" + schoolId;
    }

    /** Call after writing a key so the next read sees the new value. */
    public void evict(String configName) {
        cache.remove(configName);
    }

    public void evictAll() {
        cache.clear();
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private static Long idOf(School school) {
        return school == null ? null : school.getId();
    }

    private static Long requireSchoolId(School school) {
        Long id = idOf(school);
        if (id == null) {
            throw new TcValidationException("No school is selected for this login.");
        }
        return id;
    }

    /** Insert or update one key of the school; the cache entry is dropped so the next read is fresh. */
    private void write(String keyFamily, Long schoolId, String value, String description) {
        String name = key(keyFamily, schoolId);
        if (value.length() > 255) {
            throw new TcValidationException("Value is too long for " + name + " (maximum 255 characters).");
        }
        SystemConfig config = systemConfigRepository.findByConfigName(name).orElseGet(() -> {
            SystemConfig created = new SystemConfig();
            created.setConfigName(name);
            return created;
        });
        config.setConfigValue(value);
        config.setDescription(description);
        config.setSchoolIds(String.valueOf(schoolId));
        systemConfigRepository.save(config);
        evict(name);
        log.info("TC config saved: {}", name);
    }

    private Optional<String> read(String keyFamily, Long schoolId) {
        if (schoolId == null) {
            return Optional.empty();
        }
        String name = key(keyFamily, schoolId);
        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(name);
        if (cached != null && now - cached.loadedAt < CACHE_TTL_MILLIS) {
            return cached.value;
        }
        try {
            Optional<String> value = systemConfigRepository.findByConfigName(name)
                    .map(SystemConfig::getConfigValue)
                    .map(String::trim);
            cache.put(name, new CacheEntry(value, now));
            return value;
        } catch (Exception e) {
            // Fail safe: defaults apply (feature off) and nothing is cached.
            log.warn("Could not read TC config {}, using the default", name, e);
            return Optional.empty();
        }
    }

    private boolean readBoolean(String keyFamily, Long schoolId, boolean defaultValue) {
        Optional<String> raw = read(keyFamily, schoolId);
        if (raw.isEmpty() || raw.get().isEmpty()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(raw.get());
    }

    private long readLong(String keyFamily, Long schoolId, long defaultValue) {
        Optional<String> raw = read(keyFamily, schoolId);
        if (raw.isEmpty() || raw.get().isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw.get());
        } catch (NumberFormatException e) {
            log.warn("Invalid number '{}' in {}, using the default {}", raw.get(), key(keyFamily, schoolId), defaultValue);
            return defaultValue;
        }
    }

    private Set<Long> readIdSet(String keyFamily, Long schoolId) {
        Optional<String> raw = read(keyFamily, schoolId);
        if (raw.isEmpty() || raw.get().isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (String part : raw.get().split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(trimmed));
            } catch (NumberFormatException e) {
                log.warn("Ignoring invalid grade id '{}' in {}", trimmed, key(keyFamily, schoolId));
            }
        }
        return Collections.unmodifiableSet(ids);
    }

    private static final class CacheEntry {
        private final Optional<String> value;
        private final long loadedAt;

        private CacheEntry(Optional<String> value, long loadedAt) {
            this.value = value;
            this.loadedAt = loadedAt;
        }
    }
}
