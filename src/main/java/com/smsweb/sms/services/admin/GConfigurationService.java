package com.smsweb.sms.services.admin;

import com.smsweb.sms.repositories.admin.GConfigurationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GConfigurationService {

    private final GConfigurationRepository configurationRepository;

    @Autowired
    public GConfigurationService(GConfigurationRepository configurationRepository) {
        this.configurationRepository = configurationRepository;
    }
}
