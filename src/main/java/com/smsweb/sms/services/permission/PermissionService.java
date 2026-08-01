package com.smsweb.sms.services.permission;

import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.models.permission.UserPermission;
import com.smsweb.sms.repositories.permission.UserPermissionRepository;
import com.smsweb.sms.repositories.users.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    @Autowired private UserPermissionRepository permissionRepo;
    @Autowired private UserRepository userRepo;

    /**
     * Per-user permission cache, keyed by UserEntity.id — NOT per-HttpSession.
     *
     * Previously this cached into the caller's HttpSession, which meant
     * evictCache(userId) could only ever clear the session of whoever called it
     * (the admin editing permissions on the Set Permissions screen), never the
     * target user's own session. A user who was already logged in when their
     * permissions changed kept seeing stale access until their session expired
     * or they logged out again.
     *
     * Keying by userId instead means evictCache(userId) reliably clears exactly
     * the record that changed, and that user's very next permission check
     * re-reads fresh data from the DB - no re-login required.
     *
     * Same in-memory, single-JVM caching approach already used elsewhere in this
     * app (see ParentSessionStore, LoginAttemptService, PasswordResetAttemptService).
     * No TTL/sweep is needed here since the map size is naturally bounded by the
     * number of distinct users who have had a permission check performed, not by
     * an unbounded stream of tokens.
     */
    private final Map<Long, Map<String, Set<AccessType>>> permissionCache = new ConcurrentHashMap<>();

    // ── Primary access check ─────────────────────────────────────────────────

    /**
     * Returns true if the current user has the required AccessType on the given screen.
     * Super-admin and admin roles bypass all checks and always return true.
     */
    public boolean hasAccess(String screenKey, AccessType requiredType) {
        log.debug("Inside hasAccess");
        if (isSuperOrAdmin()) return true;
        Set<AccessType> granted = getGrantedTypes(screenKey);
        return switch (requiredType) {
            case VIEW   -> granted.contains(AccessType.VIEW);
            case CREATE -> granted.contains(AccessType.CREATE);
            case EDIT   -> granted.contains(AccessType.EDIT);
            case DELETE -> granted.contains(AccessType.DELETE);
            case ALL    -> granted.containsAll(EnumSet.of(
                                AccessType.VIEW, AccessType.CREATE,
                                AccessType.EDIT, AccessType.DELETE));
            default     -> false;
        };
    }

    /**
     * Returns the effective "summary" AccessType for a screen.
     * Useful for single-value comparisons in templates or legacy code.
     *   empty set → NOTHING
     *   all four  → ALL
     *   otherwise → highest individual type present (VIEW < CREATE < EDIT < DELETE)
     */
    public AccessType getAccessType(String screenKey) {
        log.debug("Inside getAccessType");
        if (isSuperOrAdmin()) return AccessType.ALL;
        Set<AccessType> granted = getGrantedTypes(screenKey);
        if (granted.isEmpty()) return AccessType.NOTHING;
        if (granted.containsAll(EnumSet.of(
                AccessType.VIEW, AccessType.CREATE, AccessType.EDIT, AccessType.DELETE))) {
            return AccessType.ALL;
        }
        // Return whichever single type is most prominent (priority order)
        for (AccessType t : new AccessType[]{
                AccessType.DELETE, AccessType.EDIT, AccessType.CREATE, AccessType.VIEW}) {
            if (granted.contains(t)) return t;
        }
        return AccessType.NOTHING;
    }

    /**
     * Returns the raw set of AccessTypes granted to the current user for a screen.
     * Returns an empty set when no permission record exists.
     */
    public Set<AccessType> getGrantedTypes(String screenKey) {
        log.debug("Inside getGrantedTypes");
        return getPermissionsForCurrentUser()
                .getOrDefault(screenKey, Collections.emptySet());
    }

    /** Evict the cached permissions for this specific user after an admin saves changes. */
    public void evictCache(Long userId) {
        log.info("Inside evictCache - userId={}", userId);
        permissionCache.remove(userId);
    }

    // ── Internal cache ───────────────────────────────────────────────────────

    private Map<String, Set<AccessType>> getPermissionsForCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = userRepo.findByUsername(auth.getName()).getId();

        return permissionCache.computeIfAbsent(userId, id -> {
            List<UserPermission> perms = permissionRepo.findAllByUserId(id);
            Map<String, Set<AccessType>> map = new HashMap<>();
            for (UserPermission p : perms) {
                // Defensive copy so the cached set is independent of the JPA entity
                map.put(p.getScreen().getScreenKey(), new HashSet<>(p.getAccessTypes()));
            }
            return map;
        });
    }

    private boolean isSuperOrAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_SUPERADMIN"))
                || auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
