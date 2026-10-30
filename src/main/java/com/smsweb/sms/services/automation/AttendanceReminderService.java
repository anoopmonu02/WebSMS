package com.smsweb.sms.services.automation;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.Holiday;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.student.AttendanceConfirmation;
import com.smsweb.sms.repositories.admin.AcademicyearRepository;
import com.smsweb.sms.repositories.admin.HolidayRepository;
import com.smsweb.sms.repositories.admin.SchoolRepository;
import com.smsweb.sms.repositories.student.AttendanceConfirmationRepository;
import com.smsweb.sms.repositories.student.AttendanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * "Attendance not confirmed" reminder. Unconfirmed attendance is hidden from parents in the mobile
 * app, so the admin gets an email when today's attendance is missing or saved but not confirmed.
 */
@Service
public class AttendanceReminderService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceReminderService.class);
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy", Locale.ENGLISH);

    private final AcademicyearRepository academicyearRepository;
    private final HolidayRepository holidayRepository;
    private final SchoolRepository schoolRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceConfirmationRepository confirmationRepository;
    private final AutomationMailService mailService;
    private final AutomationSettingsService settingsService;

    public AttendanceReminderService(AcademicyearRepository academicyearRepository,
                                     HolidayRepository holidayRepository,
                                     SchoolRepository schoolRepository,
                                     AttendanceRepository attendanceRepository,
                                     AttendanceConfirmationRepository confirmationRepository,
                                     AutomationMailService mailService,
                                     AutomationSettingsService settingsService) {
        this.settingsService = settingsService;
        this.academicyearRepository = academicyearRepository;
        this.holidayRepository = holidayRepository;
        this.schoolRepository = schoolRepository;
        this.attendanceRepository = attendanceRepository;
        this.confirmationRepository = confirmationRepository;
        this.mailService = mailService;
    }

    /**
     * Checks today's attendance for the school and emails the admin if it is not confirmed.
     *
     * @param secondReminder true for the optional follow-up (subject gets a "Reminder:" prefix)
     * @return true when the job is finished for today (email sent, or nothing needed: Sunday, holiday,
     *         no active academic year, already confirmed); an exception means "try again shortly"
     */
    public boolean checkAndNotify(AutomationSettings settings, LocalDate today, boolean secondReminder) throws Exception {
        if (settings.getReminderRecipients().isEmpty()) {
            return true; // nobody to tell; nothing to retry
        }
        Long schoolId = settings.getSchoolId();
        if (today.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return true;
        }
        AcademicYear year = academicyearRepository.findActiveBySchoolId(schoolId);
        if (year == null) {
            log.info("Attendance reminder skipped: school {} has no active academic year", schoolId);
            return true;
        }
        if (isHoliday(schoolId, year.getId(), today)) {
            return true;
        }
        Optional<AttendanceConfirmation> confirmation =
                confirmationRepository.findBySchool_IdAndAcademicYear_IdAndAttendanceDate(schoolId, year.getId(), today);
        if (confirmation.isPresent() && confirmation.get().isConfirmed()) {
            return true;
        }

        boolean marked = attendanceRepository.existsAnyAttendanceForToday(schoolId, year.getId()) > 0;
        String schoolName = schoolRepository.findById(schoolId).map(School::getSchoolName).orElse("School " + schoolId);
        String subject = (secondReminder ? "Reminder: " : "") + "Attendance not confirmed today - " + schoolName;
        mailService.send(settings, settings.getReminderRecipients(), subject, buildBody(schoolName, today, marked, year.getId(), schoolId), false);
        settingsService.markSent(AutomationSettingsService.JOB_REMINDER, schoolId);
        return true;
    }

    private String buildBody(String schoolName, LocalDate today, boolean marked, Long yearId, Long schoolId) {
        StringBuilder sb = new StringBuilder();
        sb.append("School: ").append(schoolName).append('\n');
        sb.append("Date: ").append(today.format(DAY_FORMAT)).append("\n\n");
        if (!marked) {
            sb.append("Today's attendance has not been marked yet, so it cannot be confirmed.\n");
        } else {
            long present = 0;
            long absent = 0;
            List<Object[]> rows = attendanceRepository.getTodaysAttendanceTotals(schoolId, yearId);
            if (rows != null && !rows.isEmpty() && rows.get(0) != null && rows.get(0).length == 2) {
                Object[] r = rows.get(0);
                present = r[0] != null ? ((Number) r[0]).longValue() : 0;
                absent = r[1] != null ? ((Number) r[1]).longValue() : 0;
            }
            sb.append("Today's attendance is saved (").append(present).append(" present, ")
              .append(absent).append(" absent) but has NOT been confirmed.\n");
        }
        sb.append("\nUntil it is confirmed, parents cannot see today's attendance in the mobile app.\n");
        sb.append("Please open Attendance and confirm it.\n");
        return sb.toString();
    }

    private boolean isHoliday(Long schoolId, Long yearId, LocalDate day) {
        List<Holiday> holidays = holidayRepository.findAllByAcademicYear_IdAndSchool_IdOrderByIdAsc(yearId, schoolId);
        for (Holiday h : holidays) {
            LocalDate start = h.getHolidayStartDate();
            LocalDate end = h.getHolidayEndDate() != null ? h.getHolidayEndDate() : start;
            if (start != null && !day.isBefore(start) && !day.isAfter(end)) {
                return true;
            }
        }
        return false;
    }
}
