package com.smsweb.sms.services.automation;

import com.smsweb.sms.models.admin.SystemConfig;
import com.smsweb.sms.models.automation.AllClearMessage;
import com.smsweb.sms.repositories.admin.SystemConfigRepository;
import com.smsweb.sms.repositories.automation.AllClearMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The per-school list of all-clear messages. The six built-in lines are copied in the first time a school
 * opens the list (remembered by a system_config flag, so deleting them all stays deleted). When the list is
 * empty or every line is switched off, a built-in line is used, so the email is never blank.
 */
@Service
public class AllClearMessageService {

    public static final int MAX_MESSAGES = 20;
    private static final String SEEDED_KEY = "ALL_CLEAR_SEEDED_";
    private static final String GONE = "That message no longer exists.";

    private final AllClearMessageRepository repository;
    private final SystemConfigRepository systemConfigRepository;
    private final Random random = new Random();
    /** Last message id used per school (0 = a built-in fallback line), so the same line is not sent twice in a row. */
    private final Map<Long, Long> lastUsed = new ConcurrentHashMap<>();

    public AllClearMessageService(AllClearMessageRepository repository, SystemConfigRepository systemConfigRepository) {
        this.repository = repository;
        this.systemConfigRepository = systemConfigRepository;
    }

    /** All messages of the school, copying the built-ins in on the very first call. */
    @Transactional
    public List<AllClearMessage> list(Long schoolId) {
        String key = SEEDED_KEY + schoolId;
        if (systemConfigRepository.findByConfigName(key).isEmpty() && repository.countBySchoolId(schoolId) == 0) {
            for (String text : AllClearMessages.DEFAULTS) {
                repository.save(newMessage(schoolId, text));
            }
        }
        if (systemConfigRepository.findByConfigName(key).isEmpty()) {
            SystemConfig c = new SystemConfig();
            c.setConfigName(key);
            c.setConfigValue("true");
            c.setDescription("The built-in all-clear messages were copied into the list of school " + schoolId + ".");
            c.setSchoolIds(String.valueOf(schoolId));
            systemConfigRepository.save(c);
        }
        return repository.findBySchoolIdOrderByIdAsc(schoolId);
    }

    /** @return null on success, otherwise a message for the user */
    @Transactional
    public String add(Long schoolId, String text) {
        String clean = clean(text);
        String error = validate(clean);
        if (error != null) {
            return error;
        }
        if (repository.countBySchoolId(schoolId) >= MAX_MESSAGES) {
            return "You can keep at most " + MAX_MESSAGES + " messages. Delete one first.";
        }
        repository.save(newMessage(schoolId, clean));
        return null;
    }

    @Transactional
    public String update(Long schoolId, Long id, String text) {
        String clean = clean(text);
        String error = validate(clean);
        if (error != null) {
            return error;
        }
        Optional<AllClearMessage> found = repository.findByIdAndSchoolId(id, schoolId);
        if (found.isEmpty()) {
            return GONE;
        }
        found.get().setMessageText(clean);
        repository.save(found.get());
        return null;
    }

    @Transactional
    public String setActive(Long schoolId, Long id, boolean active) {
        Optional<AllClearMessage> found = repository.findByIdAndSchoolId(id, schoolId);
        if (found.isEmpty()) {
            return GONE;
        }
        found.get().setActive(active);
        repository.save(found.get());
        return null;
    }

    @Transactional
    public String delete(Long schoolId, Long id) {
        Optional<AllClearMessage> found = repository.findByIdAndSchoolId(id, schoolId);
        if (found.isEmpty()) {
            return GONE;
        }
        repository.delete(found.get());
        return null;
    }

    /** Adds back any of the six built-in lines that are missing (up to the limit). Existing lines stay as they are. */
    @Transactional
    public int restoreDefaults(Long schoolId) {
        list(schoolId);
        Set<String> existing = new HashSet<>();
        long count = 0;
        for (AllClearMessage m : repository.findBySchoolIdOrderByIdAsc(schoolId)) {
            existing.add(m.getMessageText());
            count++;
        }
        int added = 0;
        for (String text : AllClearMessages.DEFAULTS) {
            if (!existing.contains(text) && count < MAX_MESSAGES) {
                repository.save(newMessage(schoolId, text));
                count++;
                added++;
            }
        }
        return added;
    }

    /** A random active message with "{days}" filled in; never the same one twice in a row when there is a choice. */
    public String pickRendered(Long schoolId, int days) {
        List<AllClearMessage> active = new ArrayList<>();
        for (AllClearMessage m : repository.findBySchoolIdOrderByIdAsc(schoolId)) {
            if (m.isActive()) {
                active.add(m);
            }
        }
        long last = lastUsed.getOrDefault(schoolId, -1L);
        if (active.isEmpty()) {
            int idx = AllClearMessages.nextIndex(random, (int) last);
            lastUsed.put(schoolId, (long) idx);
            return AllClearMessages.render(idx, days);
        }
        AllClearMessage chosen = active.get(random.nextInt(active.size()));
        if (active.size() > 1 && chosen.getId() != null && chosen.getId() == last) {
            chosen = active.get((active.indexOf(chosen) + 1) % active.size());
        }
        lastUsed.put(schoolId, chosen.getId() == null ? -1L : chosen.getId());
        return render(chosen.getMessageText(), days);
    }

    static String render(String text, int days) {
        return text.replace("{days}", String.valueOf(days));
    }

    /** One line, no control characters, trimmed. */
    static String clean(String text) {
        return text == null ? "" : text.replaceAll("[\\p{Cntrl}]+", " ").trim();
    }

    private static String validate(String clean) {
        if (clean.isEmpty()) {
            return "Please type a message.";
        }
        if (clean.length() > AllClearMessage.MAX_LENGTH) {
            return "A message can have at most " + AllClearMessage.MAX_LENGTH + " characters.";
        }
        return null;
    }

    private static AllClearMessage newMessage(Long schoolId, String text) {
        AllClearMessage m = new AllClearMessage();
        m.setSchoolId(schoolId);
        m.setMessageText(text);
        m.setActive(true);
        return m;
    }
}
