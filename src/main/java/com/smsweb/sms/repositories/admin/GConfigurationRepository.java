package com.smsweb.sms.repositories.admin;

import com.smsweb.sms.models.admin.GConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GConfigurationRepository extends JpaRepository<GConfiguration, Long> {
}
