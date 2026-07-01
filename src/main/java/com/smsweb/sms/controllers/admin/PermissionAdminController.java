package com.smsweb.sms.controllers.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.models.Users.Employee;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.admin.School;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.models.permission.AppScreen;
import com.smsweb.sms.models.permission.UserPermission;
import com.smsweb.sms.repositories.employee.EmployeeRepository;
import com.smsweb.sms.repositories.permission.AppScreenRepository;
import com.smsweb.sms.repositories.permission.UserPermissionRepository;
import com.smsweb.sms.repositories.users.UserRepository;
import com.smsweb.sms.services.Employee.EmployeeService;
import com.smsweb.sms.services.permission.PermissionService;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Admin controller for managing fine-grained user permissions.
 *
 * URL structure:
 *   GET  /admin/permissions              → list users (scoped to school for ROLE_ADMIN)
 *   GET  /admin/permissions/user/{id}    → permission matrix for one user
 *   POST /admin/permissions/save         → AJAX multi-select save
 *
 * School scoping:
 *   - ROLE_SUPERADMIN sees all users across all schools.
 *   - ROLE_ADMIN sees only users belonging to their own school, and can only
 *     modify permissions for those same users (prevents cross-school tampering).
 */
@Controller
@RequestMapping("/admin/permissions")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN')")
public class PermissionAdminController extends BaseController {
    private static final Logger log = LoggerFactory.getLogger(PermissionAdminController.class);


    @Autowired private AppScreenRepository screenRepo;
    @Autowired private UserPermissionRepository permRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private PermissionService permissionService;
    @Autowired private EmployeeService employeeService;
    @Autowired private EmployeeRepository employeeRepository;

    // ── List users ────────────────────────────────────────────────────────────

    /**
     * SUPERADMIN → sees every user in the system.
     * ADMIN      → sees only users whose employee record belongs to their school.
     */
    @GetMapping
    public String listUsers(Model model) {
        log.info("Inside listUsers");
        List<UserEntity> users;
        if (isSuperAdmin()) {
            users = userRepo.findAllWithRoles();
        } else {
            // School-scoped view: only show users from the admin's school
            School adminSchool = getAdminSchool();
            if (adminSchool == null) {
                users = Collections.emptyList();
            } else {
                users = userRepo.findAllBySchoolIdWithRoles(adminSchool.getId());
            }
        }
        model.addAttribute("users", users);
        model.addAttribute("hasUsers", !users.isEmpty());
        return "admin/permission";
    }

    // ── Permission matrix ─────────────────────────────────────────────────────

    /**
     * GET /admin/permissions/user/{userId}
     *
     * Model attributes sent to the template:
     *   user       → UserEntity being configured
     *   screens    → Map<String, List<AppScreen>>  grouped by module
     *   currentMap → Map<Long, Set<String>>  screenId → set of AccessType names already saved
     *                (String names so Thymeleaf can use .contains('VIEW') without SpEL type refs)
     */
    @GetMapping("/user/{userId}")
    public String userPermissions(@PathVariable Long userId, Model model) {
        log.info("Inside userPermissions");
        UserEntity user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // School isolation: ADMIN cannot open the permission matrix for users outside their school
        if (!isSuperAdmin() && !userBelongsToAdminSchool(user)) {
            return "redirect:/access-denied";
        }

        Map<String, List<AppScreen>> screensByModule = screenRepo.findAll()
                .stream()
                .collect(Collectors.groupingBy(AppScreen::getModule));

        // Convert Set<AccessType> → Set<String> so Thymeleaf can call .contains('VIEW')
        Map<Long, Set<String>> currentMap = permRepo.findAllByUserId(userId)
                .stream()
                .collect(Collectors.toMap(
                        p -> p.getScreen().getId(),
                        p -> p.getAccessTypes().stream()
                                .map(AccessType::name)
                                .collect(Collectors.toSet())
                ));

        model.addAttribute("user", user);
        model.addAttribute("screens", screensByModule);
        model.addAttribute("currentMap", currentMap);
        return "admin/user-permissions";
    }

    // ── Save (AJAX) ───────────────────────────────────────────────────────────

    /**
     * POST /admin/permissions/save
     *
     * Request body (JSON):
     * {
     *   "userId": 5,
     *   "permissions": {
     *     "12": ["VIEW", "CREATE"],
     *     "13": ["VIEW"],
     *     "14": []
     *   }
     * }
     * Keys = AppScreen.id (as strings — JSON keys are always strings).
     * Values = list of AccessType names to grant (empty list = no access).
     *
     * Response:
     *   200 { "status": "saved", "updated": N }
     *   400 { "status": "error", "message": "..." }
     */
    @PostMapping("/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> savePermissions(
            @RequestBody PermissionSaveRequest request) {
        log.info("Inside savePermissions");

        if (request == null || request.getUserId() == null)
            return badRequest("userId is required");
        if (request.getPermissions() == null || request.getPermissions().isEmpty())
            return badRequest("permissions map is required and must not be empty");

        UserEntity user = userRepo.findById(request.getUserId()).orElse(null);
        if (user == null)
            return badRequest("No user found with id: " + request.getUserId());

        // School isolation guard: ADMIN cannot modify permissions for users outside their school
        if (!isSuperAdmin() && !userBelongsToAdminSchool(user)) {
            return badRequest("Access denied: cannot modify permissions for users outside your school");
        }

        int updatedCount = 0;
        for (Map.Entry<Long, List<String>> entry : request.getPermissions().entrySet()) {
            Long screenId = entry.getKey();
            List<String> rawTypes = entry.getValue();

            AppScreen screen = screenRepo.findById(screenId).orElse(null);
            if (screen == null) continue;   // screen removed — skip silently

            // Parse and validate each AccessType name in the list
            Set<AccessType> accessTypes = new HashSet<>();
            for (String raw : rawTypes) {
                if (raw == null || raw.isBlank()) continue;
                try {
                    AccessType t = AccessType.valueOf(raw.trim().toUpperCase());
                    // Only store the four actionable types; skip NOTHING / ALL shortcuts
                    if (t != AccessType.NOTHING && t != AccessType.ALL) {
                        accessTypes.add(t);
                    }
                } catch (IllegalArgumentException e) {
                    return badRequest("Unknown AccessType value: '" + raw + "' for screen id " + screenId);
                }
            }

            // Upsert the UserPermission row
            UserPermission perm = permRepo
                    .findByUserIdAndScreenScreenKey(user.getId(), screen.getScreenKey())
                    .orElseGet(UserPermission::new);

            perm.setUser(user);
            perm.setScreen(screen);
            perm.setAccessTypes(accessTypes);   // replaces entire set
            permRepo.save(perm);
            updatedCount++;
        }

        permissionService.evictCache(user.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("status", "saved");
        response.put("updated", updatedCount);
        return ResponseEntity.ok(response);
    }

    // ── DTO ───────────────────────────────────────────────────────────────────

    public static class PermissionSaveRequest {

        private Long userId;

        /** screenId → list of AccessType names to grant (empty list = revoke all) */
        private Map<Long, List<String>> permissions;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public Map<Long, List<String>> getPermissions() { return permissions; }
        public void setPermissions(Map<Long, List<String>> permissions) {
            this.permissions = permissions;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** True when the currently logged-in user has ROLE_SUPERADMIN. */
    private boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_SUPERADMIN"));
    }

    /**
     * Returns the School the logged-in ADMIN belongs to (via their Employee record).
     * Returns null if the admin has no employee record (shouldn't happen in practice).
     */
    private School getAdminSchool() {
        try {
            return employeeService.getLoggedInEmployeeSchool();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns true if the given user's employee record belongs to the same school
     * as the currently logged-in ADMIN. Prevents cross-school permission tampering.
     */
    private boolean userBelongsToAdminSchool(UserEntity user) {
        School adminSchool = getAdminSchool();
        if (adminSchool == null) return false;
        Employee emp = employeeRepository.findByUserEntity(user);
        return emp != null && emp.getSchool() != null
                && emp.getSchool().getId().equals(adminSchool.getId());
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("status", "error");
        error.put("message", message);
        return ResponseEntity.badRequest().body(error);
    }
}
