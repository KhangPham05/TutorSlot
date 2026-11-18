package com.tutorslot.controller.api;

import com.tutorslot.dto.LoginRequest;
import com.tutorslot.dto.LoginResponse;
import com.tutorslot.dto.MeDto;
import com.tutorslot.model.User;
import com.tutorslot.security.TokenService;
import com.tutorslot.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthApiController {

    private final TokenService tokenService;
    private final CurrentUserService currentUserService;

    public AuthApiController(TokenService tokenService, CurrentUserService currentUserService) {
        this.tokenService = tokenService;
        this.currentUserService = currentUserService;
    }

    // Open to everyone (see ApiSecurityConfig). A wrong email or password becomes a 401 in
    // ApiExceptionHandler.
    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return tokenService.login(request.email(), request.password());
    }

    // Who the token belongs to. authentication.getName() is the token's subject (the email), so
    // the user comes from the signed token, never from anything the client sends.
    @GetMapping("/me")
    public MeDto me(Authentication authentication) {
        User user = currentUserService.requireByEmail(authentication.getName());
        return new MeDto(user.email(), user.fullName(), user.role());
    }
}
