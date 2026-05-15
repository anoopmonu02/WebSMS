package com.smsweb.sms.controllers.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.smsweb.sms.controllers.BaseController;
import com.smsweb.sms.models.Users.UserEntity;
import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.models.permission.AppScreen;
import com.smsweb.sms.models.permission.UserPermission;
import com.smsweb.sms.repositories.permission.AppScreenRepository;
import com.smsweb.sms.repositories.permission.UserPermissionRepository;
import com.smsweb.sms.repositories.users.UserRepository;
import com.smsweb.sms.services.permission.PermissionService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin controller for managing fine-grained user permissions.
 *
 * URL structure:
 *   GET  /admin/permissions              → list all users
 *   GET  /admin/permissions/user/{id}    → permission matrix for one user
 *   POST /admin/permissions/save         → AJAX multi-select save
 */
@Controller
@RequestMapping("/admin/permissions")
@PreAuthorize("hasAnyRole('ROLE_ADMIN','ROLE_SUPERADMIN')")
public class PermissionAdminController extends BaseController {

    @Autowired private AppScreenRepository screenRepo;
    @Autowired private UserPermissionRepository permRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private PermissionService permissionService;

    // ── List users ────────────────────────────────────────────────────────────

    @GetMapping
    public String listUsers(Model model) {
        List<UserEntity> users = userRepo.findAllWithRoles();
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
        UserEntity user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

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

        if (request == null || request.getUserId() == null)
            return badRequest("userId is required");
        if (request.getPermissions() == null || request.getPermissions().isEmpty())
            return badRequest("permissions map is required and must not be empty");

        UserEntity user = userRepo.findById(request.getUserId()).orElse(null);
        if (user == null)
            return badRequest("No user found with id: " + request.getUserId());

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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("status", "error");
        error.put("message", message);
        return ResponseEntity.badRequest().body(error);
    }
}
