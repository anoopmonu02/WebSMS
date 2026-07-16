package com.smsweb.sms.controllers.mobile;

import com.smsweb.sms.dto.mobile.ApiResponse;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Attendance;
import com.smsweb.sms.services.mobile.MobileAcademicYearService;
import com.smsweb.sms.services.student.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.*;

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

    public MobileAttendanceController(StudentService studentService,
                                       MobileAcademicYearService academicYearService) {
        this.studentService = studentService;
        this.academicYearService = academicYearService;
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

        List<Map<String, Object>> days = new ArrayList<>();
        int present = 0, absent = 0;

        for (Attendance a : records) {
            LocalDate day = a.getAttendanceDate().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate();

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
}
