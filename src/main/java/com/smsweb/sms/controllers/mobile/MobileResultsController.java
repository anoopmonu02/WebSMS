package com.smsweb.sms.controllers.mobile;

import com.smsweb.sms.dto.mobile.ApiResponse;
import com.smsweb.sms.models.student.ExamResultSummary;
import com.smsweb.sms.services.student.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Exam result endpoints for the student mobile app.
 *
 * GET /api/v1/results   — all exam results for the current academic year
 *
 * Uses StudentService.getStudentExamResults() — added to StudentService for mobile.
 */
@RestController
@RequestMapping("/api/v1/results")
public class MobileResultsController {
    private static final Logger log = LoggerFactory.getLogger(MobileResultsController.class);


    private final StudentService studentService;

    public MobileResultsController(StudentService studentService) {
        this.studentService = studentService;
    }

    // ── GET /api/v1/results ───────────────────────────────────────────────────

    /**
     * Returns all exam results for the authenticated student.
     *
     * Each entry: {
     *   "examId":        1,
     *   "examName":      "Half Yearly",
     *   "examDate":      "2024-11-15",
     *   "resultDate":    "2024-12-01",
     *   "result":        "PASS",
     *   "totalMarks":    400,
     *   "obtainedMarks": 345,
     *   "percentage":    86.25,
     *   "division":      "FIRST DIVISION",
     *   "remarks":       "",
     *   "isDeclared":    true
     * }
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getResults(
            HttpServletRequest request) {
        log.info("Inside getResults");

        Long academicStudentId = (Long) request.getAttribute("academicStudentId");
        Long schoolId          = (Long) request.getAttribute("schoolId");
        Long academicYearId    = (Long) request.getAttribute("academicYearId");

        // Uses StudentService.getStudentExamResults() — added to StudentService
        List<ExamResultSummary> results =
                studentService.getStudentExamResults(academicStudentId, schoolId, academicYearId);

        List<Map<String, Object>> response = new ArrayList<>();

        for (ExamResultSummary r : results) {
            Map<String, Object> entry = new LinkedHashMap<>();

            // Exam details (name, declared date)
            boolean hasExam = r.getExamDetails() != null;
            entry.put("examId",        hasExam ? r.getExamDetails().getId() : null);
            entry.put("examName",      hasExam && r.getExamDetails().getExamination() != null
                                           ? r.getExamDetails().getExamination().getExaminationName() : "N/A");
            entry.put("examDate",      hasExam ? r.getExamDetails().getExamDeclaredDate() : null);

            // Result data
            entry.put("resultDate",    r.getExamResultDate());
            entry.put("result",        r.getResult());
            entry.put("totalMarks",    r.getTotalMarks());
            entry.put("obtainedMarks", r.getObtainedMarks());
            entry.put("percentage",    r.getPercentageMarks());
            entry.put("division",      r.getDivision());
            entry.put("remarks",       r.getRemarks() != null ? r.getRemarks() : "");
            entry.put("isDeclared",    r.getResult() != null && !r.getResult().isBlank());

            response.add(entry);
        }

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
