package com.smsweb.sms.services.permission;

import com.smsweb.sms.models.permission.AccessType;
import com.smsweb.sms.models.permission.AppScreen;
import com.smsweb.sms.models.permission.UserPermission;
import com.smsweb.sms.repositories.permission.UserPermissionRepository;
import com.smsweb.sms.repositories.users.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PermissionService {

    private static final String SESSION_KEY = "USER_PERMISSIONS";

    @Autowired private UserPermissionRepository permissionRepo;
    @Autowired private UserRepository userRepo;
    @Autowired private HttpSession session;

    /** Primary check — call this from controllers or AOP */
    public boolean hasAccess(String screenKey, AccessType requiredType) {
        if (isSuperOrAdmin()) return true;
        Map<String, AccessType> perms = getPermissionsForCurrentUser();
        AccessType granted = perms.getOrDefault(screenKey, AccessType.NOTHING);
        return switch (requiredType) {
            case VIEW   -> granted.canView();
            case CREATE -> granted.canCreate();
            case EDIT   -> granted.canEdit();
            case DELETE -> granted.canDelete();
            case ALL    -> granted == AccessType.ALL;
            default     -> false;
        };
    }

    public AccessType getAccessType(String screenKey) {
        if (isSuperOrAdmin()) return AccessType.ALL;
        return getPermissionsForCurrentUser().getOrDefault(screenKey, AccessType.NOTHING);
    }

    /** Call this after admin saves permissions to clear stale cache */
    public void evictCache(Long userId) {
        // In a real multi-session setup you'd store by userId.
        // For simplicity, clear this session's cache if it's the same user.
        session.removeAttribute(SESSION_KEY);
    }

    @SuppressWarnings("unchecked")
    private Map<String, AccessType> getPermissionsForCurrentUser() {
        Map<String, AccessType> cached = (Map<String, AccessType>) session.getAttribute(SESSION_KEY);
        if (cached != null) return cached;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        Long userId = userRepo.findByUsername(username).getId();

        List<UserPermission> perms = permissionRepo.findAllByUserId(userId);
        Map<String, AccessType> map = new HashMap<>();
        for (UserPermission p : perms) {
            map.put(p.getScreen().getScreenKey(), p.getAccessType());
        }
        session.setAttribute(SESSION_KEY, map);
        return map;
    }

    private boolean isSuperOrAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_SUPERADMIN"))
                || auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
