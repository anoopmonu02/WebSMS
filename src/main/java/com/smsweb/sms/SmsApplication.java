package com.smsweb.sms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

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
        // Pin the JVM default timezone explicitly, first thing, before any Spring
        // context/date handling code runs — several date-parsing spots in this app
        // (see FeeSubmissionService) rely on TimeZone.getDefault() rather than an
        // explicit zone, which silently follows whatever the host OS is set to.
        // This app is India-only (fee dates, receipts, IST business hours), so pin
        // it here regardless of server environment instead of trusting deployment
        // config alone.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(SmsApplication.class, args);
    }

}
