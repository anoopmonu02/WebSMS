package com.smsweb.sms.services.student;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbsentMonthlyReportServiceTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 18);

    @Test
    void absentWeekdayCounts() {
        assertTrue(AbsentMonthlyReportService.isAbsentDay(false, MONDAY, null, "Active"));
        assertTrue(AbsentMonthlyReportService.isAbsentDay(false, MONDAY, "", null));
    }

    @Test
    void presentSundayAndHolidayRowsDoNotCount() {
        assertFalse(AbsentMonthlyReportService.isAbsentDay(true, MONDAY, null, "Active"));
        assertFalse(AbsentMonthlyReportService.isAbsentDay(false, SUNDAY, null, "Active"));
        assertFalse(AbsentMonthlyReportService.isAbsentDay(false, MONDAY, "Diwali HOLIDAY", "Active"));
        assertFalse(AbsentMonthlyReportService.isAbsentDay(false, MONDAY, null, "Holiday"));
    }

    @Test
    void limitIsClamped() {
        assertEquals(7, AbsentMonthlyReportService.clampLimit(null));
        assertEquals(1, AbsentMonthlyReportService.clampLimit(0));
        assertEquals(31, AbsentMonthlyReportService.clampLimit(99));
        assertEquals(10, AbsentMonthlyReportService.clampLimit(10));
    }

    @Test
    void serialNumbersSortNumericallyWithBlanksLast() {
        assertTrue(AbsentMonthlyReportService.compareSr("2", "10") < 0);
        assertTrue(AbsentMonthlyReportService.compareSr("10", "2") > 0);
        assertTrue(AbsentMonthlyReportService.compareSr("A1", "B1") < 0);
        assertTrue(AbsentMonthlyReportService.compareSr(null, "1") > 0);
        assertTrue(AbsentMonthlyReportService.compareSr("1", "") < 0);
        assertEquals(0, AbsentMonthlyReportService.compareSr(null, ""));
    }

    @Test
    void monthKeyAndLabel() {
        assertEquals("2026-04", AbsentMonthlyReportService.key(YearMonth.of(2026, 4)));
        assertEquals("Apr", AbsentMonthlyReportService.label(YearMonth.of(2026, 4)));
    }
}
