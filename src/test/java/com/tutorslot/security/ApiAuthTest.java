package com.tutorslot.security;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Instant;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The JWT login the Next.js frontend uses, through the real security filters and the seeded users:
// the right password gets a signed token, and only a valid token from this app opens the API.
// Reads only, so the cached context can be shared.
@SpringBootTest
@AutoConfigureMockMvc
class ApiAuthTest {

    private static final String DAN = "dan.student@sjsu.edu";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private AppUserDetailsService userDetailsService;

    @Test
    void theRightPasswordGetsATokenThatOpensTheApi() throws Exception {
        String token = loginAs(DAN);

        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(DAN))
                .andExpect(jsonPath("$.fullName").value("Dan Kim"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void aWrongPasswordAndAnUnknownEmailGetTheSame401AndNoToken() throws Exception {
        for (String email : new String[] {DAN, "nobody@sjsu.edu"}) {
            mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                            .content(credentials(email, "wrong-password")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Wrong email or password."))
                    .andExpect(jsonPath("$.token").doesNotExist());
        }
    }

    @Test
    void aBlankFieldIs400WithAMessage() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content(credentials(DAN, "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Enter your password."));
    }

    @Test
    void theApiRejectsNoTokenAForgedTokenAndAnExpiredToken() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());

        // Signed with some other key: looks like a real token, but the signature doesn't match.
        byte[] otherKey = new byte[32];
        new SecureRandom().nextBytes(otherKey);
        JwtEncoder forger = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(otherKey, "HmacSHA256")));
        String forged = sign(forger, Instant.now().plusSeconds(3600));
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());

        // Signed with the app's own key, but expired an hour ago.
        String expired = sign(jwtEncoder, Instant.now().minusSeconds(3600));
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aWebLoginSessionDoesNotOpenTheApi() throws Exception {
        // A session exactly as the web login form leaves it: works for the web pages...
        MockHttpSession session = new MockHttpSession();
        UserPrincipal dan = (UserPrincipal) userDetailsService.loadUserByUsername(DAN);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, new SecurityContextImpl(
                UsernamePasswordAuthenticationToken.authenticated(dan, null, dan.getAuthorities())));
        mockMvc.perform(get("/customer/assistant").session(session))
                .andExpect(status().isOk());

        // ...but the API ignores it. That's why the API can switch CSRF off safely.
        mockMvc.perform(get("/api/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    private String loginAs(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(credentials(email, "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private static String sign(JwtEncoder encoder, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(TokenService.ISSUER)
                .subject(DAN)
                .issuedAt(expiresAt.minusSeconds(7200))
                .expiresAt(expiresAt)
                .claim(TokenService.ROLE_CLAIM, "CUSTOMER")
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
