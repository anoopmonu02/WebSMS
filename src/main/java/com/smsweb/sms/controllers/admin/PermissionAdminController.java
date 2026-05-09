package com.smsweb.sms.controllers.admin;

// ── Spring MVC ────────────────────────────────────────────────────────────────
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

// ── Spring Security ───────────────────────────────────────────────────────────
// (none beyond @PreAuthorize above — role check is enough at class level)

// ── Project: base controller ──────────────────────────────────────────────────
import com.smsweb.sms.controllers.BaseController;

// ── Project: models ───────────────────────────────────────────────────────────
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.models.permission.AppScreen;
import com.smsweb.sms.models.permission.UserPermission;

// ── Project: repositories ─────────────────────────────────────────────────────
import com.smsweb.sms.repositories.permission.AppScreenRepository;
import com.smsweb.sms.repositories.permission.UserPermissionRepository;
import com.smsweb.sms.repositories.users.UserRepository;

// ── Project: services ─────────────────────────────────────────────────────────
import com.smsweb.sms.services.permission.PermissionService;

// ── Java / JDK ────────────────────────────────────────────────────────────────
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin controller for managing fine-grained user permissions.
 *
 * URL structure:
 *   GET  /admin/permissions              → list all users to pick from
 *   GET  /admin/permissions/user/{id}    → permission matrix for one user
 *   POST /admin/permissions/save         → AJAX save of the permission matrix
 */
@Controller
@RequestMapping("/admin/permissions")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN')")
public class PermissionAdminController extends BaseController {

    @Autowired private AppScreenRepository screenRepo;
    @Autowired private UserPermissionRepository permRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private PermissionService permissionService;

    // ─────────────────────────────────────────────────────────────────────────
    // LIST — pick a user
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /admin/permissions
     * Shows all users. Admin clicks one to manage that user's permissions.
     */
    @GetMapping
    public String listUsers(Model model) {
        List<UserEntity> users = userRepo.findAllWithRoles();
        model.addAttribute("users", users);
        model.addAttribute("hasUsers", !users.isEmpty());
        return "admin/permission";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PERMISSION MATRIX — one user
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /admin/permissions/user/{userId}
     * Shows the full permission matrix for one user, grouped by module.
     * The template receives:
     *   user        → UserEntity being configured
     *   screens     → Map<String , List<AppScreen>>  (grouped by module)
            *   currentMap  → Map<Long , AccessType>  (what is already granted)
            *   accessTypes → AccessType[]  (all options for the select box)
            */
    @GetMapping("/user/{userId}")
    public String userPermissions(@PathVariable Long userId, Model model) {
        UserEntity user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // All screens grouped by module for rendering section headers
        Map<String, List<AppScreen>> screensByModule = screenRepo.findAll()
                .stream()
                .collect(Collectors.groupingBy(AppScreen::getModule));

        // Current grants keyed by screen.id so the template can look them up cheaply
        Map<Long, AccessType> currentMap = permRepo.findAllByUserId(userId)
                .stream()
                .collect(Collectors.toMap(
                        p -> p.getScreen().getId(),
                        UserPermission::getAccessType
                ));

        model.addAttribute("user", user);
        model.addAttribute("screens", screensByModule);
        model.addAttribute("currentMap", currentMap);
        model.addAttribute("accessTypes", AccessType.values());
        return "admin/user-permissions";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SAVE — AJAX POST from the permission matrix form
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * POST /admin/permissions/save
     *
     * Request body (JSON):
     * {
     *   "userId": 5,
     *   "permissions": {
     *     "1": "VIEW",
     *     "2": "ALL",
     *     "3": "NOTHING",
     *     "4": "CREATE"
     *   }
     * }
     * Keys in "permissions" are AppScreen.id values (as strings — JSON keys are
     * always strings).  Values are AccessType enum name strings.
     *
     * Response:
     *   200 { "status": "saved",  "updated": 4 }
     *   400 { "status": "error",  "message": "..." }   — bad input
     *   500 { "status": "error",  "message": "..." }   — unexpected failure
     *
     * After saving, the in-memory permission cache for that user is evicted so
     * changes take effect on their next request without requiring a logout.
     */
    @PostMapping("/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> savePermissions(
            @RequestBody PermissionSaveRequest request) {

        // ── 1. Validate the incoming payload ──────────────────────────────────
        if (request == null || request.getUserId() == null) {
            return badRequest("userId is required");
        }
        if (request.getPermissions() == null || request.getPermissions().isEmpty()) {
            return badRequest("permissions map is required and must not be empty");
        }

        // ── 2. Resolve the target user ────────────────────────────────────────
        UserEntity user = userRepo.findById(request.getUserId()).orElse(null);
        if (user == null) {
            return badRequest("No user found with id: " + request.getUserId());
        }

        // ── 3. Upsert one UserPermission row per screen entry ─────────────────
        int updatedCount = 0;
        for (Map.Entry<Long, String> entry : request.getPermissions().entrySet()) {

            Long screenId = entry.getKey();
            String accessTypeStr = entry.getValue();

            // Resolve the screen
            AppScreen screen = screenRepo.findById(screenId).orElse(null);
            if (screen == null) {
                // Skip unknown screen IDs silently — screen may have been removed
                continue;
            }

            // Parse the AccessType safely
            AccessType accessType;
            try {
                accessType = AccessType.valueOf(accessTypeStr.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return badRequest("Unknown AccessType value: '" + accessTypeStr
                        + "' for screen id " + screenId);
            }

            // Find existing row or create a new one (upsert pattern)
            UserPermission perm = permRepo
                    .findByUserIdAndScreenScreenKey(user.getId(), screen.getScreenKey())
                    .orElseGet(UserPermission::new);

            perm.setUser(user);
            perm.setScreen(screen);
            perm.setAccessType(accessType);
            permRepo.save(perm);
            updatedCount++;
        }

        // ── 4. Evict the session cache so changes are instant ─────────────────
        permissionService.evictCache(user.getId());

        // ── 5. Return success response ────────────────────────────────────────
        Map<String, Object> response = new HashMap<>();
        response.put("status", "saved");
        response.put("updated", updatedCount);
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DTO — inner static class for the POST body
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Request DTO for the savePermissions endpoint.
     *
     * Must be a plain class with getters/setters — Jackson requires this for
     * @RequestBody deserialization (records do not work reliably here).
     *
     * JSON keys in "permissions" arrive as strings from JavaScript; they are
     * declared as Map<Long, String> and Jackson converts the numeric string keys
     * to Long automatically.
     */
    public static class PermissionSaveRequest {

        private Long userId;

        /** screen.id (Long) → AccessType name (String) */
        private Map<Long, String> permissions;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public Map<Long, String> getPermissions() { return permissions; }
        public void setPermissions(Map<Long, String> permissions) { this.permissions = permissions; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("status", "error");
        error.put("message", message);
        return ResponseEntity.badRequest().body(error);
    }
}
