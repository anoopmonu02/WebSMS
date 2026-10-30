package com.smsweb.sms.services.admin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BirthdayNotificationSettingsServiceTest {

    @Test
    void cronIsConvertedToTime() {
        assertEquals("08:00", BirthdayNotificationSettingsService.cronToTime("0 0 8 * * *"));
        assertEquals("14:05", BirthdayNotificationSettingsService.cronToTime("0 5 14 * * *"));
    }

    @Test
    void unreadableCronFallsBackToDefaultTime() {
        assertEquals("08:00", BirthdayNotificationSettingsService.cronToTime("garbage"));
    }

    @Test
    void timeIsConvertedToCron() {
        assertEquals("0 0 6 * * *", BirthdayNotificationSettingsService.timeToCron("06:00"));
        assertEquals("0 59 23 * * *", BirthdayNotificationSettingsService.timeToCron("23:59"));
    }

    @Test
    void onlyValidTimesAreAccepted() {
        assertTrue(BirthdayNotificationSettingsService.isValidTime("00:00"));
        assertTrue(BirthdayNotificationSettingsService.isValidTime("23:59"));
        for (String bad : new String[]{"24:00", "6:00", "12:60", "", "abc", "12:00:00", null}) {
            assertFalse(BirthdayNotificationSettingsService.isValidTime(bad), String.valueOf(bad));
        }
    }
}
