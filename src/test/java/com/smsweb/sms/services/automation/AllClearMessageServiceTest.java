package com.smsweb.sms.services.automation;

import com.smsweb.sms.models.admin.SystemConfig;
import com.smsweb.sms.models.automation.AllClearMessage;
import com.smsweb.sms.repositories.admin.SystemConfigRepository;
import com.smsweb.sms.repositories.automation.AllClearMessageRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AllClearMessageServiceTest {

    private final List<AllClearMessage> table = new ArrayList<>();
    private final Map<String, SystemConfig> config = new LinkedHashMap<>();
    private long nextId = 1;
    private final AllClearMessageService service;

    AllClearMessageServiceTest() {
        AllClearMessageRepository repo = (AllClearMessageRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{AllClearMessageRepository.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "findBySchoolIdOrderByIdAsc": {
                            List<AllClearMessage> out = new ArrayList<>();
                            for (AllClearMessage x : table) if (x.getSchoolId().equals(a[0])) out.add(x);
                            return out;
                        }
                        case "countBySchoolId": {
                            long n = 0;
                            for (AllClearMessage x : table) if (x.getSchoolId().equals(a[0])) n++;
                            return n;
                        }
                        case "findByIdAndSchoolId":
                            for (AllClearMessage x : table) if (x.getId().equals(a[0]) && x.getSchoolId().equals(a[1])) return Optional.of(x);
                            return Optional.empty();
                        case "save": {
                            AllClearMessage x = (AllClearMessage) a[0];
                            if (x.getId() == null) { x.setId(nextId++); table.add(x); }
                            return x;
                        }
                        case "delete": table.remove(a[0]); return null;
                        default: throw new UnsupportedOperationException(m.getName());
                    }
                });
        SystemConfigRepository cfg = (SystemConfigRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{SystemConfigRepository.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "findByConfigName": return Optional.ofNullable(config.get((String) a[0]));
                        case "save": SystemConfig c = (SystemConfig) a[0]; config.put(c.getConfigName(), c); return c;
                        default: throw new UnsupportedOperationException(m.getName());
                    }
                });
        service = new AllClearMessageService(repo, cfg);
    }

    @Test
    void firstOpenCopiesTheSixDefaultsOnlyOnce() {
        assertEquals(6, service.list(1L).size());
        assertEquals(6, service.list(1L).size());
        for (AllClearMessage m : new ArrayList<>(table)) service.delete(1L, m.getId());
        assertEquals(0, service.list(1L).size()); // stays empty after the user deleted all
    }

    @Test
    void addEditSwitchDeleteAndLimits() {
        service.list(1L);
        assertNull(service.add(1L, "  Hello {days}  "));
        assertNotNull(service.add(1L, "   "));
        assertNotNull(service.add(1L, "x".repeat(201)));
        AllClearMessage mine = table.get(table.size() - 1);
        assertEquals("Hello {days}", mine.getMessageText());
        assertNull(service.update(1L, mine.getId(), "Changed"));
        assertEquals("Changed", mine.getMessageText());
        assertNull(service.setActive(1L, mine.getId(), false));
        assertFalse(mine.isActive());
        assertNotNull(service.delete(2L, mine.getId())); // another school cannot touch it
        assertNull(service.delete(1L, mine.getId()));
        while (table.size() < AllClearMessageService.MAX_MESSAGES) assertNull(service.add(1L, "m" + table.size()));
        assertNotNull(service.add(1L, "one too many"));
    }

    @Test
    void controlCharactersAreFlattened() {
        service.list(1L);
        assertNull(service.add(1L, "line1\nline2\t<b>"));
        assertEquals("line1 line2 <b>", table.get(table.size() - 1).getMessageText());
    }

    @Test
    void pickNeverRepeatsAndFillsDays() {
        service.list(1L);
        String previous = null;
        for (int i = 0; i < 60; i++) {
            String msg = service.pickRendered(1L, 9);
            assertFalse(msg.contains("{days}"));
            assertNotEquals(previous, msg);
            previous = msg;
        }
    }

    @Test
    void fallsBackToBuiltInWhenEverythingIsOff() {
        service.list(1L);
        for (AllClearMessage m : table) service.setActive(1L, m.getId(), false);
        String msg = service.pickRendered(1L, 7);
        assertTrue(msg.length() > 10);
    }

    @Test
    void restoreAddsOnlyMissingDefaults() {
        service.list(1L);
        service.delete(1L, table.get(0).getId());
        assertEquals(1, service.restoreDefaults(1L));
        assertEquals(0, service.restoreDefaults(1L));
    }
}
