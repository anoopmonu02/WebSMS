package com.smsweb.sms.services.automation;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class AutomationScheduleUtilTest {

    private static LocalDateTime at(String s) { return LocalDateTime.parse(s); }

    @Test
    void timeParsing() {
        assertTrue(AutomationScheduleUtil.isValidTime("00:00"));
        assertTrue(AutomationScheduleUtil.isValidTime("23:59"));
        assertFalse(AutomationScheduleUtil.isValidTime("24:00"));
        assertFalse(AutomationScheduleUtil.isValidTime("9:30"));
        assertFalse(AutomationScheduleUtil.isValidTime(null));
        assertEquals(LocalTime.of(8, 5), AutomationScheduleUtil.parseTime("08:05"));
        assertNull(AutomationScheduleUtil.parseTime("nope"));
    }

    @Test
    void dueWindowIsTargetUntilGrace() {
        LocalTime t = LocalTime.of(11, 0);
        assertFalse(AutomationScheduleUtil.isDueNow(at("2026-10-20T10:59:59"), t, 0, 30));
        assertTrue(AutomationScheduleUtil.isDueNow(at("2026-10-20T11:00:00"), t, 0, 30));
        assertTrue(AutomationScheduleUtil.isDueNow(at("2026-10-20T11:30:00"), t, 0, 30));
        assertFalse(AutomationScheduleUtil.isDueNow(at("2026-10-20T11:30:01"), t, 0, 30));
    }

    @Test
    void secondReminderUsesOffset() {
        LocalTime t = LocalTime.of(11, 0);
        assertFalse(AutomationScheduleUtil.isDueNow(at("2026-10-20T11:30:00"), t, 60, 30));
        assertTrue(AutomationScheduleUtil.isDueNow(at("2026-10-20T12:00:00"), t, 60, 30));
        // an offset that would push the slot past midnight never fires early the next morning
        assertFalse(AutomationScheduleUtil.isDueNow(at("2026-10-20T00:10:00"), LocalTime.of(23, 30), 60, 30));
        assertFalse(AutomationScheduleUtil.isDueNow(at("2026-10-20T23:40:00"), LocalTime.of(23, 30), 60, 30));
    }

    @Test
    void windowIsCutAtMidnight() {
        assertTrue(AutomationScheduleUtil.isDueNow(at("2026-10-20T23:59:30"), LocalTime.of(23, 50), 0, 30));
    }

    @Test
    void reportDays() {
        LocalDate mon = LocalDate.of(2026, 10, 19), sun = LocalDate.of(2026, 10, 18);
        assertTrue(AutomationScheduleUtil.isReportDay(mon, AutomationScheduleUtil.Frequency.DAILY, null, null));
        assertFalse(AutomationScheduleUtil.isReportDay(sun, AutomationScheduleUtil.Frequency.DAILY, null, null));
        assertTrue(AutomationScheduleUtil.isReportDay(mon, AutomationScheduleUtil.Frequency.WEEKLY, DayOfWeek.MONDAY, null));
        assertFalse(AutomationScheduleUtil.isReportDay(mon, AutomationScheduleUtil.Frequency.WEEKLY, DayOfWeek.TUESDAY, null));
        assertTrue(AutomationScheduleUtil.isReportDay(LocalDate.of(2026, 10, 5), AutomationScheduleUtil.Frequency.MONTHLY, null, "5"));
        assertFalse(AutomationScheduleUtil.isReportDay(LocalDate.of(2026, 10, 6), AutomationScheduleUtil.Frequency.MONTHLY, null, "5"));
        assertTrue(AutomationScheduleUtil.isReportDay(LocalDate.of(2026, 2, 28), AutomationScheduleUtil.Frequency.MONTHLY, null, "LAST"));
        assertFalse(AutomationScheduleUtil.isReportDay(LocalDate.of(2028, 2, 28), AutomationScheduleUtil.Frequency.MONTHLY, null, "LAST"));
        assertTrue(AutomationScheduleUtil.isReportDay(LocalDate.of(2028, 2, 29), AutomationScheduleUtil.Frequency.MONTHLY, null, "last"));
        assertFalse(AutomationScheduleUtil.isReportDay(mon, AutomationScheduleUtil.Frequency.MONTHLY, null, "abc"));
    }

    @Test
    void lastExpectedBackupSlotDaily() {
        List<LocalTime> two = List.of(LocalTime.of(2, 0), LocalTime.of(14, 30));
        assertEquals(at("2026-10-20T14:30:00"), AutomationScheduleUtil.lastExpectedBackupSlot(at("2026-10-20T15:00:00"), false, null, two));
        assertEquals(at("2026-10-20T02:00:00"), AutomationScheduleUtil.lastExpectedBackupSlot(at("2026-10-20T09:00:00"), false, null, two));
        // before today's first slot: yesterday's later slot is the last expected one
        assertEquals(at("2026-10-19T14:30:00"), AutomationScheduleUtil.lastExpectedBackupSlot(at("2026-10-20T01:00:00"), false, null, two));
        assertEquals(at("2026-10-20T02:00:00"), AutomationScheduleUtil.lastExpectedBackupSlot(at("2026-10-20T02:00:00"), false, null, two));
        assertNull(AutomationScheduleUtil.lastExpectedBackupSlot(at("2026-10-20T09:00:00"), false, null, List.of()));
    }

    @Test
    void lastExpectedBackupSlotWeekly() {
        List<LocalTime> one = List.of(LocalTime.of(2, 0));
        // 20 Oct 2026 is a Tuesday; the last Sunday slot was 18 Oct 02:00
        assertEquals(at("2026-10-18T02:00:00"), AutomationScheduleUtil.lastExpectedBackupSlot(at("2026-10-20T10:00:00"), true, DayOfWeek.SUNDAY, one));
        // on the Sunday itself, before the slot, the previous Sunday applies
        assertEquals(at("2026-10-11T02:00:00"), AutomationScheduleUtil.lastExpectedBackupSlot(at("2026-10-18T01:00:00"), true, DayOfWeek.SUNDAY, one));
    }

    @Test
    void minutesApartWrapsAroundMidnight() {
        assertEquals(10, AutomationScheduleUtil.minutesApart(LocalTime.of(23, 55), LocalTime.of(0, 5)));
        assertEquals(690, AutomationScheduleUtil.minutesApart(LocalTime.of(2, 0), LocalTime.of(14, 30)));
        assertEquals(0, AutomationScheduleUtil.minutesApart(LocalTime.of(2, 0), LocalTime.of(2, 0)));
    }

    @Test
    void backupCronParsing() {
        AutomationScheduleUtil.BackupCron daily = AutomationScheduleUtil.parseBackupCron("0 30 2 * * *");
        assertFalse(daily.weekly);
        assertEquals(LocalTime.of(2, 30), daily.time);
        AutomationScheduleUtil.BackupCron weekly = AutomationScheduleUtil.parseBackupCron("0 0 2 ? * SUN");
        assertTrue(weekly.weekly);
        assertEquals(DayOfWeek.SUNDAY, weekly.dayOfWeek);
        assertNull(AutomationScheduleUtil.parseBackupCron("garbage"));
        assertNull(AutomationScheduleUtil.parseBackupCron(null));
        assertNull(AutomationScheduleUtil.parseBackupCron("0 0 2 * * XYZ").dayOfWeek);
    }

    @Test
    void allClearNeverRepeatsAndFillsDays() {
        Random r = new Random(42);
        int last = -1;
        for (int i = 0; i < 500; i++) {
            int next = AllClearMessages.nextIndex(r, last);
            assertTrue(next >= 0 && next < AllClearMessages.DEFAULTS.size());
            assertNotEquals(last, next);
            last = next;
        }
        for (int i = 0; i < AllClearMessages.DEFAULTS.size(); i++) {
            assertFalse(AllClearMessages.render(i, 7).contains("{days}"));
        }
        assertTrue(AllClearMessages.render(0, 9).contains("9"));
    }
}
