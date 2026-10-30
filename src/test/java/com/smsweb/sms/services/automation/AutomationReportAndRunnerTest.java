package com.smsweb.sms.services.automation;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AutomationReportAndRunnerTest {

    private static AbsenceStreakRow row(String name) {
        return new AbsenceStreakRow(name, "5", "A", "12", "Father", "9999999999", 8,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10));
    }

    @Test
    void reportEscapesHtmlFromDatabase() {
        List<AbsenceStreakRow> rows = List.of(row("<script>alert(1)</script>"));
        String html = AbsenceReportService.buildReportHtml("A & B School", rows, 7, LocalDate.of(2026, 10, 12));
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("A &amp; B School"));
        assertTrue(html.contains("1 student has"));
        assertTrue(html.contains("12 Oct 2026"));
    }

    @Test
    void reportIsCappedAtMaxRows() {
        List<AbsenceStreakRow> rows = new ArrayList<>();
        for (int i = 0; i < AbsenceReportService.MAX_ROWS_IN_EMAIL + 5; i++) {
            rows.add(row("S" + i));
        }
        String html = AbsenceReportService.buildReportHtml("School", rows, 7, LocalDate.of(2026, 10, 12));
        assertTrue(html.contains("Showing the first " + AbsenceReportService.MAX_ROWS_IN_EMAIL));
        assertFalse(html.contains(">S" + (AbsenceReportService.MAX_ROWS_IN_EMAIL + 1) + "<"));
    }

    @Test
    void allClearEmailEscapesAndMentionsDate() {
        String html = AbsenceReportService.buildAllClearHtml("S<1>", "Hooray & cheers", LocalDate.of(2026, 10, 12));
        assertTrue(html.contains("S&lt;1&gt;"));
        assertTrue(html.contains("Hooray &amp; cheers"));
        assertTrue(html.contains("12 Oct 2026"));
    }

    @Test
    void dateConversionHandlesCommonTypes() {
        assertEquals(LocalDate.of(2026, 10, 12), AbsenceReportService.toLocalDate(java.sql.Date.valueOf("2026-10-12")));
        assertEquals(LocalDate.of(2026, 10, 12), AbsenceReportService.toLocalDate(LocalDate.of(2026, 10, 12)));
        assertEquals(LocalDate.of(2026, 10, 12), AbsenceReportService.toLocalDate("2026-10-12"));
        assertNull(AbsenceReportService.toLocalDate(null));
        assertNull(AbsenceReportService.toLocalDate("garbage"));
    }

    @Test
    void enabledSchoolsComesFromSwitchedOnKeysOnly() {
        Map<String, String> cfg = new HashMap<>();
        cfg.put("ATT_REMINDER_ON_1", "true");
        cfg.put("ABS_REPORT_ON_2", "TRUE");
        cfg.put("ABS_REPORT_ON_3", "false");
        cfg.put("ATT_REMINDER_ON_x", "true");
        cfg.put("ATT_REMINDER_TIME_9", "11:00");
        cfg.put("ABS_REPORT_ON_1", "true");
        Set<Long> ids = AutomationJobRunner.enabledSchools(cfg);
        assertEquals(2, ids.size());
        assertTrue(ids.contains(1L));
        assertTrue(ids.contains(2L));
    }
}
