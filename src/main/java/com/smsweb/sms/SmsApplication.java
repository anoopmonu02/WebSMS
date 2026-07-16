package com.smsweb.sms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling added for feature #10 (mobile refresh-token cleanup job —
// MobileRefreshTokenCleanupScheduler). This is the first scheduled task in the
// app; enabling it has no effect on anything else since nothing else uses
// @Scheduled.
@EnableScheduling
@SpringBootApplication
public class SmsApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(SmsApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(SmsApplication.class, args);
    }

}
