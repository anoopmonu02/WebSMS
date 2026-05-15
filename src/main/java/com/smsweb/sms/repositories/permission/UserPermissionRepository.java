package com.smsweb.sms.repositories.permission;

import com.smsweb.sms.models.permission.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {

    /**
     * Load all permissions for a user with their accessTypes eagerly fetched
     * in a single JOIN query (avoids N+1 selects on the ElementCollection).
     */
    @Query("SELECT DISTINCT up FROM UserPermission up LEFT JOIN FETCH up.accessTypes WHERE up.user.id = :userId")
    List<UserPermission> findAllByUserId(@Param("userId") Long userId);

    Optional<UserPermission> findByUserIdAndScreenScreenKey(Long userId, String screenKey);

    /**
     * Bulk-delete all UserPermission rows that reference a specific screen.
     * Must run before deleting the AppScreen to avoid FK constraint violations.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM UserPermission up WHERE up.screen.id = :screenId")
    void deleteByScreenId(@Param("screenId") Long screenId);
}
