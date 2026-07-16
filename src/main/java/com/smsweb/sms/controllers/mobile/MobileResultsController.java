package com.smsweb.sms.controllers.mobile;

import com.smsweb.sms.dto.mobile.ApiResponse;
import com.smsweb.sms.models.student.AcademicStudent;
import com.smsweb.sms.models.student.ExamResultSummary;
import com.smsweb.sms.services.mobile.MobileAcademicYearService;
import com.smsweb.sms.services.student.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exam result endpoints for the student mobile app.
 *
 * GET /api/v1/results[?academicStudentId=..]
 */
@RestController
@RequestMapping("/api/v1/results")
public class MobileResultsController {
    private static final Logger log = LoggerFactory.getLogger(MobileResultsController.class);

    private final StudentService studentService;                  // existing, unchanged
    private final MobileAcademicYearService academicYearService;  // new, mobile-only

    public MobileResultsController(StudentService studentService,
                                    MobileAcademicYearService academicYearService) {
        this.studentService = studentService;
        this.academicYearService = academicYearService;
    }

    // ── GET /api/v1/results ───────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getResults(
            @RequestParam(required = false) Long academicStudentId,
            HttpServletRequest request) {
        log.info("Inside getResults");

        Long jwtAcademicStudentId = (Long) request.getAttribute("academicStudentId");

        Optional<AcademicStudent> target = academicYearService
                .resolveTargetAcademicStudent(academicStudentId, jwtAcademicStudentId);
        if (target.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Requested student record is not accessible"));
        }
        AcademicStudent resolved = target.get();

        List<ExamResultSummary> results = studentService.getStudentExamResults(
                resolved.getId(), resolved.getSchool().getId(), resolved.getAcademicYear().getId());

        List<Map<String, Object>> response = new ArrayList<>();

        for (ExamResultSummary r : results) {
            Map<String, Object> entry = new LinkedHashMap<>();

            boolean hasExam = r.getExamDetails() != null;
            entry.put("examId",        hasExam ? r.getExamDetails().getId() : null);
            entry.put("examName",      hasExam && r.getExamDetails().getExamination() != null
                                           ? r.getExamDetails().getExamination().getExaminationName() : "N/A");
            entry.put("examDate",      hasExam ? r.getExamDetails().getExamDeclaredDate() : null);

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
