package com.smsweb.sms.repositories.permission;

import com.smsweb.sms.models.permission.AppScreen;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AppScreenRepository extends JpaRepository<AppScreen, Long> {
    Optional<AppScreen> findByScreenKey(String screenKey);
}
