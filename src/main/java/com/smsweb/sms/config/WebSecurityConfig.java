package com.smsweb.sms.config;


import com.smsweb.sms.services.users.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.security.PublicKey;

@Configuration
@EnableMethodSecurity
public class WebSecurityConfig {





    @Bean
    public  UserDetailsService userDetailsService() {
        return new UserDetailsServiceImpl();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }



    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(requests -> requests
                        // Public assets
                        .requestMatchers("/images/**", "/css/**", "/js/**", "/fonts/**",
                                "/images/students/**", "/images/employees/**").permitAll()
                        .requestMatchers("/auth/forgot-password", "/auth/reset-password").permitAll()
                        .requestMatchers("/login", "/register").permitAll()
                        .requestMatchers("/error", "/error/**").permitAll()

                        // Student-only portal (blocked from employee areas)
                        .requestMatchers("/student-portal/**").hasRole("STUDENT")

                        // Admin-only paths
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "SUPERADMIN")

                        // Employee paths — all non-student staff
                        .requestMatchers("/employee/**").hasAnyRole("ADMIN", "SUPERADMIN", "TEACHER", "ACCOUNTENT")
                        .requestMatchers("/student/**").hasAnyRole("ADMIN", "SUPERADMIN", "ACCOUNTENT")
                        .requestMatchers("/fees/**").hasAnyRole("ADMIN", "SUPERADMIN", "ACCOUNTENT")
                        .requestMatchers("/sibling/**").hasAnyRole("ADMIN", "SUPERADMIN", "ACCOUNTENT")
                        .requestMatchers("/universal/**").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers("/message/**").hasAnyRole("ADMIN", "SUPERADMIN", "TEACHER", "ACCOUNTENT")

                        // Dashboard accessible to all authenticated non-student users
                        .requestMatchers("/dashboard").hasAnyRole("ADMIN", "SUPERADMIN", "TEACHER", "ACCOUNTENT")

                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(customAuthenticationSuccessHandler())
                        .failureHandler(customAuthenticationFailureHandler())
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                        .logoutSuccessUrl("/login?logout=true")
                        .permitAll()
                )
                .exceptionHandling(ex -> ex
                        // When a logged-in user hits a URL they're not allowed — redirect to /access-denied
                        .accessDeniedPage("/access-denied")
                )
                .userDetailsService(userDetailsService());

        return http.build();
    }


    @Bean
    public CustomAuthenticationFailureHandler customAuthenticationFailureHandler() {
        return new CustomAuthenticationFailureHandler();
    }

    @Bean
    public CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler(){
        return new CustomAuthenticationSuccessHandler();
    }


}
