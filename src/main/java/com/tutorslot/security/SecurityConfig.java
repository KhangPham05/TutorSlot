package com.tutorslot.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/slots", "/login", "/css/**", "/error/**").permitAll()
                        // The mock notification service is a separate "application" reached only
                        // over HTTP+JSON with its own API key -- it has no browser session, so it
                        // can't be gated by a role rule, and the API key is its own protection.
                        .requestMatchers("/mock-notify/**").permitAll()
                        .requestMatchers("/customer/**").hasRole("CUSTOMER")
                        .requestMatchers("/provider/**").hasRole("PROVIDER")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .permitAll())
                // CSRF stays on everywhere else (Spring Security's default) -- only the mock
                // service's own endpoints are exempt, since a service-to-service caller has no
                // CSRF token to send.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/mock-notify/**"));
        // A logged-in user hitting a page they don't have the role for throws
        // AccessDeniedException, not an authentication redirect -- Spring Boot's default error
        // handling renders templates/error/403.html for that, so no custom AccessDeniedHandler is
        // needed here.
        return http.build();
    }
}
