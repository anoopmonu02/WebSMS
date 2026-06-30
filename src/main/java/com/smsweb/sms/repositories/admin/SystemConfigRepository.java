package com.smsweb.sms.repositories.admin;

import com.smsweb.sms.models.admin.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, String> {
    Optional<SystemConfig> findByConfigName(String configName);
}
