package com.tutorslot.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

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
                        // Only health and metrics are exposed (see application.properties), and
                        // both stay open for the demo and for Docker's healthcheck, which has no
                        // session either.
                        .requestMatchers("/health", "/metrics/**").permitAll()
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
                .csrf(csrf -> csrf.ignoringRequestMatchers("/mock-notify/**"))
                // A logged-in user hitting a page they don't have the role for throws
                // AccessDeniedException here, in the filter chain -- it never reaches
                // GlobalExceptionHandler, which only sees exceptions thrown from inside a
                // controller (our own ForbiddenException, for ownership checks). This is the only
                // place that role-gate rejection can be logged, so it gets its own small handler
                // that logs, then renders templates/error/403.html exactly as the default would.
                .exceptionHandling(ex -> ex.accessDeniedHandler((request, response, accessDeniedException) -> {
                    log.warn("Access denied: user={} path={}",
                            request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous",
                            request.getRequestURI());
                    new AccessDeniedHandlerImpl().handle(request, response, accessDeniedException);
                }));
        return http.build();
    }
}
