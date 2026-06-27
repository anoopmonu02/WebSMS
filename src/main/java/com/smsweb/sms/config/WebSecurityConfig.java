package com.smsweb.sms.config;


import com.smsweb.sms.config.mobile.JwtAuthenticationFilter;
import com.smsweb.sms.config.mobile.JwtTokenProvider;
import com.smsweb.sms.services.users.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * WebSecurityConfig — dual security chains.
 *
 * Chain 1 (@Order 1) — Mobile API  : /api/v1/**
 *   • STATELESS sessions (no HttpSession)
 *   • JWT filter (JwtAuthenticationFilter) validates Bearer tokens
 *   • Only /api/v1/auth/login is public; everything else requires ROLE_STUDENT
 *   • Returns JSON error responses (not redirects) for 401/403
 *
 * Chain 2 (@Order 2) — Web / Thymeleaf : all other paths
 *   • Form login, session-based (unchanged from original)
 *   • Role-based URL authorization unchanged
 */
@Configuration
@EnableMethodSecurity
public class WebSecurityConfig {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    // Inject the @Service-annotated bean so DI is properly wired (userRepository, etc.)
    @Autowired
    private UserDetailsServiceImpl userDetailsServiceImpl;

    // ── Shared beans ──────────────────────────────────────────────────────────

    @Bean
    public UserDetailsService userDetailsService() {
        // Return the Spring-managed instance (NOT new UserDetailsServiceImpl())
        // so that @Autowired fields like userRepository are properly injected.
        return userDetailsServiceImpl;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // NOTE: Do NOT expose AuthenticationManager as a @Bean here.
    // Doing so causes a ProviderManager circular-parent StackOverflowError:
    //   PM_chain.authenticate() → PM_global.authenticate() → PM_chain.authenticate() → ...
    // Spring Boot auto-configures the AuthenticationManager from the UserDetailsService bean above.

    // ── Mobile API Security Chain (Order 1 — evaluated first) ────────────────
    //
    // Handles all /api/v1/** requests.
    // - STATELESS session (no HttpSession created)
    // - JWT filter validates the Bearer token on every request
    // - /api/v1/auth/login is the only public endpoint in this chain
    // - All other /api/v1/** paths require ROLE_STUDENT
    //
    @Bean
    @Order(1)
    public SecurityFilterChain mobileApiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/v1/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(requests -> requests
                .requestMatchers("/api/v1/auth/**").permitAll()
                // Any valid JWT holder is authenticated — role check skipped here
                // because student UserEntity accounts were historically created without
                // ROLE_STUDENT assigned. The JWT itself (issued only after FamilyAccount
                // authentication) is the authorisation proof. StudentService now assigns
                // ROLE_STUDENT on new accounts, but existing accounts have no role.
                .requestMatchers("/api/v1/**").authenticated()
            )
            .addFilterBefore(
                new JwtAuthenticationFilter(jwtTokenProvider),
                UsernamePasswordAuthenticationFilter.class
            )
            // Return 401 JSON instead of redirect-to-login for API clients
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"success\":false,\"message\":\"Unauthorized – please log in\",\"data\":null}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"success\":false,\"message\":\"Access denied\",\"data\":null}");
                })
            );

        return http.build();
    }

    // ── Web (form-login) Security Chain (Order 2 — fallback) ─────────────────
    //
    // Handles all non-API routes: Thymeleaf pages, dashboard, student portal, etc.
    // This is the existing configuration — unchanged.
    //
    @Bean
    @Order(2)
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

                        // Admin-only paths — STAFF excluded intentionally (no school config access)
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "SUPERADMIN")

                        // Employee paths — all non-student staff including STAFF (sub-admin)
                        // Route-level gate is intentionally wide; @CheckAccess annotations on each
                        // controller method enforce fine-grained permission control for STAFF users.
                        .requestMatchers("/employee/**").hasAnyRole("ADMIN", "SUPERADMIN", "TEACHER", "ACCOUNTENT", "STAFF")
                        .requestMatchers("/student/import/**").hasRole("SUPERADMIN")
                        .requestMatchers("/student/**").hasAnyRole("ADMIN", "SUPERADMIN", "ACCOUNTENT", "STAFF")
                        .requestMatchers("/fees/**").hasAnyRole("ADMIN", "SUPERADMIN", "ACCOUNTENT", "STAFF")
                        .requestMatchers("/sibling/**").hasAnyRole("ADMIN", "SUPERADMIN", "ACCOUNTENT", "STAFF")
                        .requestMatchers("/universal/download-docs/**").hasAnyRole("ADMIN", "SUPERADMIN", "TEACHER", "ACCOUNTENT", "STAFF")
                        .requestMatchers("/universal/**").hasAnyRole("ADMIN", "SUPERADMIN", "STAFF")
                        .requestMatchers("/message/**").hasAnyRole("ADMIN", "SUPERADMIN", "TEACHER", "ACCOUNTENT", "STAFF")

                        // Dashboard accessible to all authenticated non-student users
                        .requestMatchers("/dashboard").hasAnyRole("ADMIN", "SUPERADMIN", "TEACHER", "ACCOUNTENT", "STAFF")

                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(customAuthenticationSuccessHandler)
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


    // Inject the @Component-managed instance so all @Autowired fields inside it are properly wired.
    @Autowired
    private CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;

    @Bean
    public CustomAuthenticationFailureHandler customAuthenticationFailureHandler() {
        return new CustomAuthenticationFailureHandler();
    }


}
