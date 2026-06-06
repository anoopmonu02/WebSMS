package com.smsweb.sms.controllers.mobile;

import com.smsweb.sms.dto.mobile.ApiResponse;
import com.smsweb.sms.models.student.Attendance;
import com.smsweb.sms.services.student.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.*;

/**
 * Attendance endpoints for the student mobile app.
 *
 * GET /api/v1/attendance/monthly?year=2025&month=4  — calendar view (day-by-day)
 * GET /api/v1/attendance/yearly                      — 12-month summary for bar chart
 *
 * Uses StudentService (existing service) — methods added to StudentService
 * call the AttendanceRepository queries added for mobile.
 */
@RestController
@RequestMapping("/api/v1/attendance")
public class MobileAttendanceController {

    private final StudentService studentService;

    public MobileAttendanceController(StudentService studentService) {
        this.studentService = studentService;
    }

    // ── GET /api/v1/attendance/monthly ───────────────────────────────────────

    /**
     * Day-by-day attendance for a requested month.
     *
     * Query params:
     *   year  — e.g. 2025
     *   month — 1-based (1=Jan … 12=Dec)
     *
     * Response: {
     *   year, month, monthName,
     *   totalWorkingDays, presentDays, absentDays, attendancePercent,
     *   days: [ { date, dayOfWeek, isPresent, remark } ]
     * }
     */
    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMonthlyAttendance(
            @RequestParam int year,
            @RequestParam int month,
            HttpServletRequest request) {

        Long academicStudentId = (Long) request.getAttribute("academicStudentId");

        LocalDate startLocal = LocalDate.of(year, month, 1);
        LocalDate endLocal   = startLocal.withDayOfMonth(startLocal.lengthOfMonth());

        // Uses StudentService.getStudentAttendanceForMonth() — added to StudentService
        List<Attendance> records = studentService.getStudentAttendanceForMonth(
                academicStudentId,
                toDate(startLocal),
                toDate(endLocal.plusDays(1))); // exclusive upper bound

        List<Map<String, Object>> days = new ArrayList<>();
        int present = 0, absent = 0;

        for (Attendance a : records) {
            LocalDate day = a.getAttendanceDate().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate();

            // isHoliday: remark contains "holiday" (case-insensitive) or status is "Holiday"
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

        int    working = records.size();
        double pct     = working > 0
                ? Math.round(present * 100.0 / working * 10.0) / 10.0 : 0.0;

        Map<String, Object> result = new LinkedHashMap<>();
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

    /**
     * 12-month attendance summary for the current academic year.
     *
     * Response: {
     *   academicYearId,
     *   totalWorkingDays, totalPresent, totalAbsent, overallPercent,
     *   months: [ { month, monthName, present, absent, total, percent } ]
     * }
     */
    @GetMapping("/yearly")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getYearlyAttendance(
            HttpServletRequest request) {

        Long academicStudentId = (Long) request.getAttribute("academicStudentId");
        Long academicYearId    = (Long) request.getAttribute("academicYearId");

        // Uses StudentService.getStudentAttendanceForYear() — added to StudentService
        List<Attendance> records = studentService.getStudentAttendanceForYear(
                academicStudentId, academicYearId);

        // Group by calendar month
        Map<Integer, int[]> stats = new TreeMap<>(); // month → [present, absent]
        for (Attendance a : records) {
            int m = a.getAttendanceDate().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate().getMonthValue();
            stats.computeIfAbsent(m, k -> new int[]{0, 0});
            if (a.isPresent()) stats.get(m)[0]++; else stats.get(m)[1]++;
        }

        List<Map<String, Object>> months = new ArrayList<>();
        int totalPresent = 0, totalAbsent = 0;

        for (Map.Entry<Integer, int[]> e : stats.entrySet()) {
            int p = e.getValue()[0], ab = e.getValue()[1], tot = p + ab;
            double pct = tot > 0 ? Math.round(p * 100.0 / tot * 10.0) / 10.0 : 0.0;

            Map<String, Object> mEntry = new LinkedHashMap<>();
            mEntry.put("month",     e.getKey());
            mEntry.put("monthName", LocalDate.of(2000, e.getKey(), 1).getMonth()
                    .getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
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
        result.put("academicYearId",   academicYearId);
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
}
