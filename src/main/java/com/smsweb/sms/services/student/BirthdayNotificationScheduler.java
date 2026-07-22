package com.smsweb.sms.services.student;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.services.admin.AcademicyearService;
import com.smsweb.sms.services.admin.SchoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * NEW component (feature: birthday notifications). Runs once daily and sends a
 * "Happy Birthday" notice (see StudentService.sendTodaysBirthdayNotifications) to
 * every student whose birthday is today, for every school's current academic year.
 *
 * Deliberately a scheduled job, not something triggered from the Dashboard page
 * load: HomeController recomputes the same "today's birthdays" list on every
 * single page view (studentService.getComingBirthDays), and the Dashboard gets
 * loaded many times a day by however many staff are logged in — hooking a send
 * into that would re-notify the same parents every time anyone views the
 * Dashboard that day, instead of once.
 *
 * Requires @EnableScheduling on the main application class — already present
 * (added earlier for MobileRefreshTokenCleanupScheduler), no change needed there.
 */
@Component
public class BirthdayNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(BirthdayNotificationScheduler.class);

    @Autowired
    private SchoolService schoolService;

    @Autowired
    private AcademicyearService academicyearService;

    @Autowired
    private StudentService studentService;

    /** Runs every day at 8:00 AM server time. */
    @Scheduled(cron = "${app.student.birthdayNotification.cron:0 0 8 * * *}")
    public void runScheduledBirthdayNotifications() {
        log.info("Scheduled birthday notification run starting");
        List<School> schools = schoolService.getAllSchools();
        for (School school : schools) {
            try {
                AcademicYear academic = academicyearService.getCurrentAcademicYear(school.getId());
                if (academic == null) {
                    log.debug("Skipping birthday notifications for school id={} — no current academic year", school.getId());
                    continue;
                }
                studentService.sendTodaysBirthdayNotifications(school, academic);
            } catch (Exception e) {
                log.error("Failed birthday notification run for school id={}", school.getId(), e);
            }
        }
        log.info("Scheduled birthday notification run finished");
    }
}
