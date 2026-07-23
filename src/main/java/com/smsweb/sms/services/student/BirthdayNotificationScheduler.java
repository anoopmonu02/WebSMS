package com.smsweb.sms.services.student;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.services.admin.AcademicyearService;
import com.smsweb.sms.services.admin.SchoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 * NOT annotated with @Scheduled anymore (feature: admin-configurable send time).
 * A fixed @Scheduled(cron = "${...}") only reads its cron expression once at
 * startup — there's no way to change it at runtime from a DB-backed admin
 * setting. Instead, this method is wired up as a trigger task by
 * DynamicSchedulingConfig (SchedulingConfigurer), whose Trigger re-reads
 * CONFIG_KEY from the system_config table every time it computes the next
 * run — so a time saved via the Birthday Notification Settings admin screen
 * takes effect starting from the very next firing, no restart needed.
 *
 * Requires @EnableScheduling on the main application class — already present
 * (added earlier for MobileRefreshTokenCleanupScheduler), no change needed there.
 */
@Component
public class BirthdayNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(BirthdayNotificationScheduler.class);

    /** system_config.config_name this job's send time is stored under (admin-set
     *  via /admin/birthday-notification-settings). Shared with DynamicSchedulingConfig. */
    public static final String CONFIG_KEY = "BIRTHDAY_NOTIFICATION_CRON";

    /** Used whenever no row exists yet in system_config for CONFIG_KEY (e.g. right
     *  after this feature is deployed, before an admin has ever saved a time) —
     *  same time this job always ran at before it became configurable. */
    public static final String DEFAULT_CRON = "0 0 8 * * *";

    @Autowired
    private SchoolService schoolService;

    @Autowired
    private AcademicyearService academicyearService;

    @Autowired
    private StudentService studentService;

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
