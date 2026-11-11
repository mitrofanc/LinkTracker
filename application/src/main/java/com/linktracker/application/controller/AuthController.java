package com.linktracker.application.controller;

import com.linktracker.application.dto.RegisterRequest;
import com.linktracker.application.dto.LoginRequest;
import com.linktracker.application.dto.TokenResponse;
import com.linktracker.application.dto.RegisterResponse;
import com.linktracker.application.service.AuthService;
import com.linktracker.application.service.LoginAttemptLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginAttemptLimiter limiter;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        limiter.check(request.email(), ip);
        TokenResponse token;
        try {
            token = authService.login(request);
        } catch (BadCredentialsException exception) {
            limiter.failed(request.email(), ip);
            throw exception;
        }
        limiter.succeeded(request.email());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(token);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }
}
