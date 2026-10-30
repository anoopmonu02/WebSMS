package com.smsweb.sms.services.automation;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.repositories.admin.AcademicyearRepository;
import com.smsweb.sms.repositories.admin.SchoolRepository;
import com.smsweb.sms.repositories.automation.AbsenceStreakRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds and emails the "students absent N or more school days in a row" report. When nobody is on the
 * list an "all clear" email with a random cheerful message goes out instead.
 */
@Service
public class AbsenceReportService {

    private static final Logger log = LoggerFactory.getLogger(AbsenceReportService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    static final int MAX_ROWS_IN_EMAIL = 300;

    private final AcademicyearRepository academicyearRepository;
    private final SchoolRepository schoolRepository;
    private final AbsenceStreakRepository streakRepository;
    private final AutomationMailService mailService;

    private final AllClearMessageService allClearMessageService;
    private final AutomationSettingsService settingsService;

    public AbsenceReportService(AcademicyearRepository academicyearRepository,
                                SchoolRepository schoolRepository,
                                AbsenceStreakRepository streakRepository,
                                AutomationMailService mailService,
                                AllClearMessageService allClearMessageService,
                                AutomationSettingsService settingsService) {
        this.allClearMessageService = allClearMessageService;
        this.settingsService = settingsService;
        this.academicyearRepository = academicyearRepository;
        this.schoolRepository = schoolRepository;
        this.streakRepository = streakRepository;
        this.mailService = mailService;
    }

    /**
     * Finds the long absentees for the school and emails the report (or the all-clear note).
     *
     * @return a short result line for the admin screen; never null. An exception means the send failed.
     */
    public String sendReport(AutomationSettings settings) throws Exception {
        if (settings.getReportRecipients().isEmpty()) {
            return "No receiver email is set for the report.";
        }
        Long schoolId = settings.getSchoolId();
        AcademicYear year = academicyearRepository.findActiveBySchoolId(schoolId);
        if (year == null) {
            return "No active academic year, so there is nothing to report.";
        }
        LocalDate latest = toLocalDate(streakRepository.findLatestMarkedDate(schoolId, year.getId()));
        if (latest == null) {
            return "No attendance has been marked yet, so there is nothing to report.";
        }
        int days = settings.getReportDays();
        List<AbsenceStreakRow> rows = findRows(schoolId, year.getId(), days);
        String schoolName = schoolRepository.findById(schoolId).map(School::getSchoolName).orElse("School " + schoolId);

        if (rows.isEmpty()) {
            String message = allClearMessageService.pickRendered(schoolId, days);
            mailService.send(settings, settings.getReportRecipients(), "All clear - no long absences - " + schoolName,
                    buildAllClearHtml(schoolName, message, latest), true);
            settingsService.markSent(AutomationSettingsService.JOB_REPORT, schoolId);
            return "All clear email sent.";
        }
        mailService.send(settings, settings.getReportRecipients(),
                "Absence alert: " + rows.size() + (rows.size() == 1 ? " student" : " students") + " absent " + days + "+ days - " + schoolName,
                buildReportHtml(schoolName, rows, days, latest), true);
        settingsService.markSent(AutomationSettingsService.JOB_REPORT, schoolId);
        return "Report sent (" + rows.size() + (rows.size() == 1 ? " student)." : " students).");
    }

    List<AbsenceStreakRow> findRows(Long schoolId, Long yearId, int days) {
        List<AbsenceStreakRow> list = new ArrayList<>();
        List<Object[]> raw = streakRepository.findLongAbsentees(schoolId, yearId, days);
        if (raw == null) {
            return list;
        }
        for (Object[] r : raw) {
            list.add(new AbsenceStreakRow(
                    str(r[1]), str(r[2]), str(r[3]), str(r[4]), str(r[5]), str(r[6]),
                    r[7] == null ? 0 : ((Number) r[7]).intValue(),
                    toLocalDate(r[8]), toLocalDate(r[9])));
        }
        return list;
    }

    // ── Email bodies (everything from the database is HTML-escaped) ─────────────────────────────

    static String buildAllClearHtml(String schoolName, String message, LocalDate latest) {
        return "<div style=\"font-family:Arial,sans-serif;font-size:14px;color:#222\">"
                + "<h3 style=\"margin:0 0 8px\">" + esc(schoolName) + "</h3>"
                + "<p style=\"font-size:16px\">" + esc(message) + "</p>"
                + "<p style=\"color:#666\">Attendance checked up to " + latest.format(DAY) + ".</p></div>";
    }

    static String buildReportHtml(String schoolName, List<AbsenceStreakRow> rows, int days, LocalDate latest) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:Arial,sans-serif;font-size:14px;color:#222\">");
        sb.append("<h3 style=\"margin:0 0 8px\">").append(esc(schoolName)).append("</h3>");
        sb.append("<p>").append(rows.size()).append(rows.size() == 1 ? " student has" : " students have")
          .append(" been absent for ").append(days).append(" or more school days in a row. ")
          .append("Sundays and holidays are not counted.</p>");
        sb.append("<p style=\"color:#666\">Attendance checked up to ").append(latest.format(DAY)).append(".</p>");
        sb.append("<table style=\"border-collapse:collapse;width:100%\"><thead><tr>");
        for (String h : new String[]{"#", "Student", "Class", "Father", "Mobile", "Days absent", "Absent since"}) {
            sb.append("<th style=\"border:1px solid #ccc;padding:6px;background:#f3f3f3;text-align:left\">").append(h).append("</th>");
        }
        sb.append("</tr></thead><tbody>");
        int shown = Math.min(rows.size(), MAX_ROWS_IN_EMAIL);
        for (int i = 0; i < shown; i++) {
            AbsenceStreakRow r = rows.get(i);
            sb.append("<tr>");
            cell(sb, String.valueOf(i + 1));
            cell(sb, r.getStudentName());
            cell(sb, r.getGrade() + " - " + r.getSection());
            cell(sb, r.getFatherName());
            cell(sb, r.getMobile());
            cell(sb, String.valueOf(r.getStreakDays()));
            cell(sb, r.getAbsentSince() == null ? "" : r.getAbsentSince().format(DAY));
            sb.append("</tr>");
        }
        sb.append("</tbody></table>");
        if (rows.size() > shown) {
            sb.append("<p style=\"color:#a00\">Showing the first ").append(shown).append(" of ").append(rows.size()).append(" students.</p>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static void cell(StringBuilder sb, String value) {
        sb.append("<td style=\"border:1px solid #ccc;padding:6px\">").append(esc(value)).append("</td>");
    }

    private static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    static LocalDate toLocalDate(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof LocalDate) {
            return (LocalDate) o;
        }
        if (o instanceof Date) {
            return ((Date) o).toLocalDate();
        }
        if (o instanceof java.util.Date) {
            return new Date(((java.util.Date) o).getTime()).toLocalDate();
        }
        try {
            return LocalDate.parse(o.toString().substring(0, 10));
        } catch (Exception e) {
            log.warn("Unreadable date value from absence query: {}", o);
            return null;
        }
    }
}
