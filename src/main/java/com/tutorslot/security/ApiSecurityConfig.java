package com.tutorslot.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

// Security for the JSON API the Next.js frontend calls. A second filter chain, separate from
// SecurityConfig: @Order(1) and securityMatcher make it handle /api/** first, so the web pages'
// session login, CSRF, and rules stay exactly as they were.
@Configuration(proxyBeanMethods = false)
public class ApiSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/customer/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/provider/**").hasRole("PROVIDER")
                        .anyRequest().authenticated())
                // Every request carries its own token, so no session is created or read: a
                // browser's login cookie from the web pages does nothing here.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF tricks a browser into sending its cookies along. This API never uses
                // cookies (the token goes in the Authorization header), so there is nothing to forge.
                .csrf(AbstractHttpConfigurer::disable)
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(roleFromToken())));
        return http.build();
    }

    // The token's "role" claim (CUSTOMER or PROVIDER) becomes ROLE_CUSTOMER or ROLE_PROVIDER, the
    // same authority the web login gives, so hasRole(...) means the same thing in both chains.
    // The user's name is the token's subject, their email, just like the web login.
    private static JwtAuthenticationConverter roleFromToken() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(TokenService.ROLE_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
