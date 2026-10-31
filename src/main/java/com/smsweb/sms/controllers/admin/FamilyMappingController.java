package com.smsweb.sms.controllers.admin;

import com.smsweb.sms.config.permission.CheckAccess;
import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.dto.FamilyMappingRowDto;
import com.smsweb.sms.models.admin.AcademicYear;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.services.globalaccess.DropdownService;
import com.smsweb.sms.services.student.FamilyMappingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin > Family Mapping. Pick a Medium, press Get, see every Active student of the
 * current session and branch with the state of their family account link, then Map /
 * Re-map one student or all pending ones. School and session always come from the
 * logged-in session, never from the request. Business logic lives in FamilyMappingService.
 */
@Controller
@RequestMapping("/admin/family-mapping")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN','ROLE_STAFF')")
public class FamilyMappingController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(FamilyMappingController.class);

    @Autowired
    private FamilyMappingService familyMappingService;
    @Autowired
    private DropdownService dropdownService;

    @CheckAccess(screen = "ADMIN_FAMILY_MAPPING", type = AccessType.VIEW)
    @GetMapping
    public String page(Model model) {
        log.info("Inside family mapping page");
        model.addAttribute("page", "datatable");   // loads the DataTables scripts/styles
        model.addAttribute("mediums", dropdownService.getMediums());
        return "admin/family-mapping";
    }

    @CheckAccess(screen = "ADMIN_FAMILY_MAPPING", type = AccessType.VIEW)
    @PostMapping("/rows")
    @ResponseBody
    public ResponseEntity<?> rows(@RequestBody Map<String, Object> body, Model model) {
        log.info("Inside family mapping rows");
        try {
            Long mediumId = parseLong(body == null ? null : body.get("mediumId"));
            if (mediumId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Please select a medium."));
            }
            School school = (School) model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
            if (school == null || academicYear == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Unable to resolve current school/academic year."));
            }
            List<FamilyMappingRowDto> rows = familyMappingService.getRows(mediumId, school.getId(), academicYear.getId());
            log.info("Family mapping rows: schoolId={}, academicYearId={}, mediumId={}, rowsFound={}",
                    school.getId(), academicYear.getId(), mediumId, rows.size());
            long mapped = rows.stream().filter(r -> FamilyMappingRowDto.MAPPED.equals(r.getState())).count();
            long noMobile = rows.stream().filter(r -> FamilyMappingRowDto.NO_MOBILE.equals(r.getState())).count();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("rows", rows);
            result.put("total", rows.size());
            result.put("mapped", mapped);
            result.put("noMobile", noMobile);
            result.put("pending", rows.size() - mapped - noMobile);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error in family mapping rows", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred."));
        }
    }

    @CheckAccess(screen = "ADMIN_FAMILY_MAPPING", type = AccessType.EDIT)
    @PostMapping("/map")
    @ResponseBody
    public ResponseEntity<?> map(@RequestBody Map<String, Object> body, Model model) {
        log.info("Inside family mapping map");
        try {
            Long studentId = parseLong(body == null ? null : body.get("studentId"));
            School school = (School) model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
            if (studentId == null || school == null || academicYear == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid request."));
            }
            return ResponseEntity.ok(familyMappingService.mapStudent(studentId, school.getId(), academicYear.getId()));
        } catch (Exception e) {
            log.error("Error in family mapping map", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred."));
        }
    }

    @CheckAccess(screen = "ADMIN_FAMILY_MAPPING", type = AccessType.EDIT)
    @PostMapping("/map-all")
    @ResponseBody
    public ResponseEntity<?> mapAll(@RequestBody Map<String, Object> body, Model model) {
        log.info("Inside family mapping map-all");
        try {
            Long mediumId = parseLong(body == null ? null : body.get("mediumId"));
            School school = (School) model.getAttribute("school");
            AcademicYear academicYear = (AcademicYear) model.getAttribute("academicYear");
            if (mediumId == null || school == null || academicYear == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid request."));
            }
            return ResponseEntity.ok(familyMappingService.mapAllPending(mediumId, school.getId(), academicYear.getId()));
        } catch (Exception e) {
            log.error("Error in family mapping map-all", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred."));
        }
    }

    private static Long parseLong(Object v) {
        if (v == null) return null;
        try {
            String s = String.valueOf(v).trim();
            return s.isEmpty() ? null : Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
