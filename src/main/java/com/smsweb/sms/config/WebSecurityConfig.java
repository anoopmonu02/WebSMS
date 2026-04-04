package com.smsweb.sms.config;


import com.smsweb.sms.services.users.UserDetailsServiceImpl;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.security.PublicKey;

@Configuration
@EnableMethodSecurity
public class WebSecurityConfig {
   private final JwtAuthFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;  // ✅ inject from ApplicationConfig

    public WebSecurityConfig(JwtAuthFilter jwtAuthFilter,
                             AuthenticationProvider authenticationProvider) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authenticationProvider = authenticationProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                //.csrf().disable()
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/**")
                )
                .authorizeHttpRequests((requests) -> requests
                        .requestMatchers("/images/**", "/css/**", "/js/**","/fonts/**","/images/students/**","/images/employees/**").permitAll()
                        .requestMatchers("/auth/forgot-password", "/auth/reset-password").permitAll() // Fixed duplicate matchers
                        .requestMatchers("/login").permitAll()
                        .requestMatchers("/admin/**", "/").hasAnyRole("ADMIN","SUPERADMIN")
                        .requestMatchers("/student/**", "/").hasAnyRole("ACCOUNTENT","SUPERADMIN","ADMIN")
                        .requestMatchers("/error", "/error/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider)
                .formLogin((form) -> form
                        .loginPage("/login")
                        .successHandler(customAuthenticationSuccessHandler())
                        .failureUrl("/login?error=true")
                        .failureHandler(customAuthenticationFailureHandler())
                        .permitAll()
                )
                .logout((logout) -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                        .logoutSuccessUrl("/login?logout=true")
                        .permitAll()
                )
                .addFilterBefore(jwtAuthFilter,                      // ✅ ADD: JWT filter
                        UsernamePasswordAuthenticationFilter.class);
                 // Disable CSRF if needed for testing

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
