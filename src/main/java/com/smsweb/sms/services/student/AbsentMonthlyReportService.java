package com.smsweb.sms.services.student;

import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.Attendance;
import com.smsweb.sms.repositories.student.AcademicStudentRepository;
import com.smsweb.sms.repositories.student.AttendanceRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Data for Student Report > "Absent Report (Monthly)": for one medium/grade/section, the number of days
 * each active student was absent in each month of the academic year.
 *
 * A day counts as absent when an attendance row says "not present" on a day that is not a Sunday and is
 * not marked as a holiday (same rule the long-absence email uses). Months that have not started yet show
 * no value. Everything is scoped by school and academic year, so ids from another school return nothing.
 */
@Service
public class AbsentMonthlyReportService {

    public static final int DEFAULT_LIMIT = 7;
    public static final int MIN_LIMIT = 1;
    public static final int MAX_LIMIT = 31;

    private final AcademicStudentRepository academicStudentRepository;
    private final AttendanceRepository attendanceRepository;

    public AbsentMonthlyReportService(AcademicStudentRepository academicStudentRepository,
                                      AttendanceRepository attendanceRepository) {
        this.academicStudentRepository = academicStudentRepository;
        this.attendanceRepository = attendanceRepository;
    }

    /** The academic year's months in order (e.g. Apr..Mar), as year-months. */
    public static List<YearMonth> academicMonths(AcademicYear year) {
        List<YearMonth> months = new ArrayList<>();
        if (year == null || year.getStartDate() == null || year.getEndDate() == null) {
            return months;
        }
        YearMonth cursor = YearMonth.from(year.getStartDate());
        YearMonth end = YearMonth.from(year.getEndDate());
        while (!cursor.isAfter(end)) {
            months.add(cursor);
            cursor = cursor.plusMonths(1);
        }
        return months;
    }

    /** Short label such as "Apr". */
    public static String label(YearMonth ym) {
        return ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }

    /** "2026-04" style key used by the page. */
    public static String key(YearMonth ym) {
        return ym.toString();
    }

    /** True when this attendance row is a real absent day (not present, not Sunday, not a holiday row). */
    static boolean isAbsentDay(boolean present, LocalDate day, String remark, String status) {
        if (present || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }
        if (remark != null && remark.toLowerCase(Locale.ROOT).contains("holiday")) {
            return false;
        }
        return status == null || !"Holiday".equals(status);
    }

    /** Clamps the red-cell limit to a sensible range. */
    public static int clampLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(MIN_LIMIT, Math.min(MAX_LIMIT, limit));
    }

    /**
     * @param monthKeys the chosen months as "yyyy-MM"; null or empty means every month of the year
     * @return the report, or a map with "error" when a chosen month is not in the academic year
     */
    public Map<String, Object> build(Long schoolId, Long mediumId, Long gradeId, Long sectionId,
                                     AcademicYear year, List<String> monthKeys, int limit, LocalDate today) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<YearMonth> yearMonths = academicMonths(year);
        if (yearMonths.isEmpty()) {
            result.put("error", "No active academic year found.");
            return result;
        }

        List<YearMonth> chosen = new ArrayList<>();
        if (monthKeys == null || monthKeys.isEmpty()) {
            chosen.addAll(yearMonths);
        } else {
            Set<String> wanted = new LinkedHashSet<>(monthKeys);
            for (YearMonth ym : yearMonths) {
                if (wanted.remove(key(ym))) {
                    chosen.add(ym);
                }
            }
            if (!wanted.isEmpty()) {
                result.put("error", "One of the selected months is not in this academic year.");
                return result;
            }
        }

        List<AcademicStudent> students = academicStudentRepository
                .findAllBySchool_IdAndMedium_IdAndGrade_IdAndSection_IdAndAcademicYear_IdAndStatusIgnoreCase(
                        schoolId, mediumId, gradeId, sectionId, year.getId(), "Active");

        Map<Long, int[]> absentByStudent = new LinkedHashMap<>();
        for (AcademicStudent s : students) {
            absentByStudent.put(s.getId(), new int[chosen.size()]);
        }

        if (!students.isEmpty()) {
            LocalDate from = chosen.get(0).atDay(1);
            LocalDate to = chosen.get(chosen.size() - 1).atEndOfMonth();
            List<Attendance> rows = attendanceRepository.findByAcademicStudentInAndAttendanceDateBetween(
                    students, toDate(from), endOfDay(to));
            for (Attendance a : rows) {
                LocalDate day = a.getAttendanceDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                int idx = chosen.indexOf(YearMonth.from(day));
                int[] counts = absentByStudent.get(a.getAcademicStudent().getId());
                if (idx >= 0 && counts != null && isAbsentDay(a.isPresent(), day, a.getRemark(), a.getStatus())) {
                    counts[idx]++;
                }
            }
        }

        List<Map<String, Object>> monthCols = new ArrayList<>();
        boolean[] started = new boolean[chosen.size()];
        for (int i = 0; i < chosen.size(); i++) {
            started[i] = !chosen.get(i).atDay(1).isAfter(today);
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("key", key(chosen.get(i)));
            col.put("label", label(chosen.get(i)));
            monthCols.add(col);
        }

        List<AcademicStudent> sorted = new ArrayList<>(students);
        sorted.sort((x, y) -> compareSr(x.getClassSrNo(), y.getClassSrNo()));

        int redCells = 0;
        int studentsWithRed = 0;
        long[] monthTotals = new long[chosen.size()];
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AcademicStudent s : sorted) {
            int[] counts = absentByStudent.get(s.getId());
            List<Integer> cells = new ArrayList<>();
            int total = 0;
            boolean anyRed = false;
            for (int i = 0; i < chosen.size(); i++) {
                if (!started[i]) {
                    cells.add(null);
                    continue;
                }
                cells.add(counts[i]);
                total += counts[i];
                monthTotals[i] += counts[i];
                if (counts[i] > limit) {
                    redCells++;
                    anyRed = true;
                }
            }
            if (anyRed) {
                studentsWithRed++;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", s.getStudent().getStudentName());
            row.put("sr", s.getClassSrNo() == null ? "" : s.getClassSrNo());
            row.put("psrn", s.getStudent().getPsrn() == null ? "" : s.getStudent().getPsrn().toString());
            row.put("cells", cells);
            row.put("total", total);
            rows.add(row);
        }

        String worstMonth = "-";
        long worst = 0;
        for (int i = 0; i < chosen.size(); i++) {
            if (started[i] && monthTotals[i] > worst) {
                worst = monthTotals[i];
                worstMonth = label(chosen.get(i));
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("students", students.size());
        summary.put("redCells", redCells);
        summary.put("studentsWithRed", studentsWithRed);
        summary.put("worstMonth", worstMonth);
        summary.put("worstMonthDays", worst);
        long totalAbsentDays = 0;
        for (long t : monthTotals) {
            totalAbsentDays += t;
        }
        summary.put("totalAbsentDays", totalAbsentDays);
        summary.put("monthsShown", chosen.size());

        result.put("limit", limit);
        result.put("months", monthCols);
        result.put("students", rows);
        result.put("summary", summary);
        return result;
    }

    private static Date toDate(LocalDate d) {
        return Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /** Last moment of the day, so rows stamped during the last day of the range are included. */
    private static Date endOfDay(LocalDate d) {
        return Date.from(d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusMillis(1));
    }

    /** Class serial numbers sort as numbers when both are numeric ("2" before "10"), otherwise as text; blanks last. */
    static int compareSr(String a, String b) {
        boolean aBlank = a == null || a.isBlank();
        boolean bBlank = b == null || b.isBlank();
        if (aBlank || bBlank) {
            return aBlank == bBlank ? 0 : (aBlank ? 1 : -1);
        }
        try {
            return Long.compare(Long.parseLong(a.trim()), Long.parseLong(b.trim()));
        } catch (NumberFormatException e) {
            return a.compareToIgnoreCase(b);
        }
    }
}
