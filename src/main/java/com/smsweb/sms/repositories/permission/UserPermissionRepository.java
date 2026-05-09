// UserPermissionRepository.java
package com.smsweb.sms.repositories.permission;

import com.smsweb.sms.models.permission.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface UserPermissionRepository extends JpaRepository<UserPermission, Long> {

    @Query("SELECT up FROM UserPermission up WHERE up.user.id = :userId")
    List<UserPermission> findAllByUserId(Long userId);

    Optional<UserPermission> findByUserIdAndScreenScreenKey(Long userId, String screenKey);
}