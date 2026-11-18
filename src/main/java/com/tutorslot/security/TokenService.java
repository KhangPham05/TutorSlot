package com.tutorslot.security;

import com.tutorslot.dto.LoginResponse;
import com.tutorslot.dto.MeDto;
import com.tutorslot.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

// API login: checks the email and password exactly like the web login form does (same users
// table, same BCrypt check), then hands back a signed token instead of starting a session.
@Service
public class TokenService {

    static final String ISSUER = "tutorslot";
    static final String ROLE_CLAIM = "role";

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final Duration timeToLive;

    public TokenService(AppUserDetailsService userDetailsService, PasswordEncoder passwordEncoder,
                        JwtEncoder jwtEncoder, @Value("${jwt.ttl-minutes:120}") long ttlMinutes) {
        // The same building blocks the form login uses. Kept private, not a bean, so the web
        // login's own setup is left untouched.
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        this.authenticationManager = new ProviderManager(provider);
        this.jwtEncoder = jwtEncoder;
        this.timeToLive = Duration.ofMinutes(ttlMinutes);
    }

    // Throws BadCredentialsException for a wrong password or an unknown email alike, so a caller
    // can't tell which emails exist.
    public LoginResponse login(String email, String password) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        User user = ((UserPrincipal) authentication.getPrincipal()).user();

        Instant now = Instant.now();
        Instant expiresAt = now.plus(timeToLive);
        // Only what the API needs to decide access: who (subject = email) and their role. No
        // password hash or personal data goes in, since anyone holding a token can read it.
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.email())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(ROLE_CLAIM, user.role())
                .build();
        String token = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        log.info("API login: user={} role={}", user.userId(), user.role());
        return new LoginResponse(token, expiresAt, new MeDto(user.email(), user.fullName(), user.role()));
    }
}
