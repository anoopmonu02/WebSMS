package com.smsweb.sms.controllers.mobile;

import com.smsweb.sms.dto.mobile.ApiResponse;
import com.smsweb.sms.models.admin.MonthMapping;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Attendance;
import com.smsweb.sms.models.student.AttendanceConfirmation;
import com.smsweb.sms.repositories.admin.MonthmappingRepository;
import com.smsweb.sms.repositories.student.AttendanceConfirmationRepository;
import com.smsweb.sms.services.mobile.MobileAcademicYearService;
import com.smsweb.sms.services.student.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Attendance endpoints for the student mobile app.
 *
 * GET /api/v1/attendance/monthly?year=2025&month=4[&academicStudentId=..]
 * GET /api/v1/attendance/yearly[?academicStudentId=..]
 *
 * academicStudentId is optional on both — omit it for "current year" (JWT
 * default), or pass a value from GET /api/v1/student/academic-years to view a
 * different enrollment year. See MobileAcademicYearService.resolveTargetAcademicStudent
 * for the ownership check that makes this safe.
 */
@RestController
@RequestMapping("/api/v1/attendance")
public class MobileAttendanceController {
    private static final Logger log = LoggerFactory.getLogger(MobileAttendanceController.class);

    private final StudentService studentService;                  // existing, unchanged
    private final MobileAcademicYearService academicYearService;  // new, mobile-only
    private final AttendanceConfirmationRepository confirmationRepository; // new, mobile-only
    private final MonthmappingRepository monthmappingRepository;  // new, mobile-only — academic-year month ordering

    public MobileAttendanceController(StudentService studentService,
                                       MobileAcademicYearService academicYearService,
                                       AttendanceConfirmationRepository confirmationRepository,
                                       MonthmappingRepository monthmappingRepository) {
        this.studentService = studentService;
        this.academicYearService = academicYearService;
        this.confirmationRepository = confirmationRepository;
        this.monthmappingRepository = monthmappingRepository;
    }

    /** Dates confirmed by admin within [start, end] for this school+academic year.
     *  A day with no row at all, or a row with isConfirmed=false, is NOT included —
     *  callers must treat both cases identically as "don't show this day yet". */
    private Set<LocalDate> confirmedDatesInRange(Long schoolId, Long academicYearId,
                                                  LocalDate start, LocalDate end) {
        List<AttendanceConfirmation> rows = confirmationRepository
                .findBySchool_IdAndAcademicYear_IdAndAttendanceDateBetween(schoolId, academicYearId, start, end);
        Set<LocalDate> confirmed = new HashSet<>();
        for (AttendanceConfirmation c : rows) {
            if (c.isConfirmed()) confirmed.add(c.getAttendanceDate());
        }
        return confirmed;
    }

    // ── GET /api/v1/attendance/monthly ───────────────────────────────────────

    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMonthlyAttendance(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) Long academicStudentId,
            HttpServletRequest request) {
        log.info("Inside getMonthlyAttendance");

        Long jwtAcademicStudentId = (Long) request.getAttribute("academicStudentId");

        Optional<AcademicStudent> target = academicYearService
                .resolveTargetAcademicStudent(academicStudentId, jwtAcademicStudentId);
        if (target.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Requested student record is not accessible"));
        }
        Long resolvedId = target.get().getId();

        LocalDate startLocal = LocalDate.of(year, month, 1);
        LocalDate endLocal   = startLocal.withDayOfMonth(startLocal.lengthOfMonth());

        List<Attendance> records = studentService.getStudentAttendanceForMonth(
                resolvedId,
                toDate(startLocal),
                toDate(endLocal.plusDays(1))); // exclusive upper bound

        // Only admin-confirmed days are shown to parents at all — an unconfirmed
        // day contributes neither a calendar entry nor a present/absent count.
        Set<LocalDate> confirmedDates = confirmedDatesInRange(
                target.get().getSchool().getId(), target.get().getAcademicYear().getId(),
                startLocal, endLocal);

        List<Map<String, Object>> days = new ArrayList<>();
        int present = 0, absent = 0;

        for (Attendance a : records) {
            LocalDate day = a.getAttendanceDate().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate();

            if (!confirmedDates.contains(day)) continue; // not yet confirmed — skip entirely

            String remark = a.getRemark() != null ? a.getRemark() : "";
            boolean isHoliday = remark.toLowerCase().contains("holiday")
                             || "Holiday".equalsIgnoreCase(a.getStatus());

            Map<String, Object> d = new LinkedHashMap<>();
            d.put("date",      day.toString());
            d.put("dayOfWeek", day.getDayOfWeek().name());
            d.put("isPresent", a.isPresent());
            d.put("isHoliday", isHoliday);
            d.put("remark",    remark);
            days.add(d);

            if (a.isPresent()) present++; else absent++;
        }

        int    working = days.size();
        double pct     = working > 0
                ? Math.round(present * 100.0 / working * 10.0) / 10.0 : 0.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("academicStudentId", resolvedId);
        result.put("year",              year);
        result.put("month",             month);
        result.put("monthName",         startLocal.getMonth()
                .getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        result.put("totalWorkingDays",  working);
        result.put("presentDays",       present);
        result.put("absentDays",        absent);
        result.put("attendancePercent", pct);
        result.put("days",              days);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── GET /api/v1/attendance/yearly ────────────────────────────────────────

    @GetMapping("/yearly")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getYearlyAttendance(
            @RequestParam(required = false) Long academicStudentId,
            HttpServletRequest request) {
        log.info("Inside getYearlyAttendance");

        Long jwtAcademicStudentId = (Long) request.getAttribute("academicStudentId");

        Optional<AcademicStudent> target = academicYearService
                .resolveTargetAcademicStudent(academicStudentId, jwtAcademicStudentId);
        if (target.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Requested student record is not accessible"));
        }
        AcademicStudent resolved = target.get();
        Long resolvedId       = resolved.getId();
        Long resolvedYearId   = resolved.getAcademicYear().getId();

        List<Attendance> records = studentService.getStudentAttendanceForYear(
                resolvedId, resolvedYearId);

        // Only admin-confirmed days count here either — derive the range to
        // check from the fetched records themselves rather than assuming
        // calendar-year bounds, since academic years don't run Jan-Dec.
        Set<LocalDate> confirmedDates = Collections.emptySet();
        if (!records.isEmpty()) {
            LocalDate minDate = null, maxDate = null;
            for (Attendance a : records) {
                LocalDate day = a.getAttendanceDate().toInstant()
                        .atZone(ZoneId.systemDefault()).toLocalDate();
                if (minDate == null || day.isBefore(minDate)) minDate = day;
                if (maxDate == null || day.isAfter(maxDate))  maxDate = day;
            }
            confirmedDates = confirmedDatesInRange(
                    resolved.getSchool().getId(), resolvedYearId, minDate, maxDate);
        }

        // Keyed by calendar month VALUE (1-12), straight from LocalDate — no
        // string-parsing ambiguity on this side. Display order comes from
        // month_mapping.priority below, so this map only needs to identify
        // which month, not sort it.
        Map<Integer, int[]> statsByMonth = new HashMap<>();
        for (Attendance a : records) {
            LocalDate day = a.getAttendanceDate().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate();
            if (!confirmedDates.contains(day)) continue; // not yet confirmed — skip entirely

            int monthKey = day.getMonthValue();
            statsByMonth.computeIfAbsent(monthKey, k -> new int[]{0, 0});
            if (a.isPresent()) statsByMonth.get(monthKey)[0]++; else statsByMonth.get(monthKey)[1]++;
        }

        // Display order follows the school's own academic-year month sequence
        // (month_mapping.priority, e.g. April..March) instead of raw calendar
        // month number — sorting by plain 1-12 put January/February/March
        // ahead of April-December once the academic year crosses into the
        // next calendar year, which is wrong for any non-Jan-Dec academic year.
        List<MonthMapping> monthMappingList = monthmappingRepository
                .findAllByAcademicYear_IdAndSchool_IdOrderByPriorityAsc(resolvedYearId, resolved.getSchool().getId())
                .stream()
                .filter(mm -> mm.getMonthMaster() != null)
                .collect(Collectors.toList());

        List<Map<String, Object>> months = new ArrayList<>();
        int totalPresent = 0, totalAbsent = 0;

        for (MonthMapping mm : monthMappingList) {
            String monthNameRaw = mm.getMonthMaster().getMonthName();
            Month resolvedMonth = parseMonthName(monthNameRaw);
            if (resolvedMonth == null) continue; // unparseable month_master row — skip rather than guess

            int[] counts = statsByMonth.get(resolvedMonth.getValue());
            if (counts == null) continue; // no confirmed attendance recorded for this month yet

            int p = counts[0], ab = counts[1], tot = p + ab;
            double pct = tot > 0 ? Math.round(p * 100.0 / tot * 10.0) / 10.0 : 0.0;

            Map<String, Object> mEntry = new LinkedHashMap<>();
            mEntry.put("month",     mm.getPriority());
            mEntry.put("monthName", resolvedMonth.getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            mEntry.put("present",   p);
            mEntry.put("absent",    ab);
            mEntry.put("total",     tot);
            mEntry.put("percent",   pct);
            months.add(mEntry);

            totalPresent += p;
            totalAbsent  += ab;
        }

        int    totalDays   = totalPresent + totalAbsent;
        double overallPct  = totalDays > 0
                ? Math.round(totalPresent * 100.0 / totalDays * 10.0) / 10.0 : 0.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("academicStudentId", resolvedId);
        result.put("academicYearId",   resolvedYearId);
        result.put("totalWorkingDays", totalDays);
        result.put("totalPresent",     totalPresent);
        result.put("totalAbsent",      totalAbsent);
        result.put("overallPercent",   overallPct);
        result.put("months",           months);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Date toDate(LocalDate d) {
        return Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /** Resolves month_master.month_name to a java.time.Month regardless of
     *  whether it's stored as a full name ("July"), a short name ("Jul"), or
     *  in any case — tried in that order. Returns null (never guesses) if
     *  nothing matches, so callers can skip a row rather than mis-map it. */
    private Month parseMonthName(String monthName) {
        if (monthName == null) return null;
        String trimmed = monthName.trim();
        if (trimmed.isEmpty()) return null;
        try {
            return Month.valueOf(trimmed.toUpperCase());
        } catch (Exception ignored) {
            // not a full enum-constant name — fall through to short-name matching
        }
        for (Month m : Month.values()) {
            if (m.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).equalsIgnoreCase(trimmed)
                    || m.getDisplayName(TextStyle.FULL, Locale.ENGLISH).equalsIgnoreCase(trimmed)) {
                return m;
            }
        }
        return null;
    }
}
