package com.linktracker.application.service;

import com.linktracker.application.dto.RegisterRequest;
import com.linktracker.application.dto.LoginRequest;
import com.linktracker.application.dto.TokenResponse;
import com.linktracker.application.dto.UserResponse;
import com.linktracker.application.dto.RegisterResponse;
import com.linktracker.application.exception.EmailAlreadyExistsException;
import com.linktracker.application.model.Role;
import com.linktracker.application.model.RoleName;
import com.linktracker.application.model.User;
import com.linktracker.application.model.UserStatus;
import com.linktracker.application.repository.RoleRepository;
import com.linktracker.application.repository.UserRepository;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User user = users.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())
                || user.getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return jwtService.issue(user);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(long userId) {
        User user = users.findById(userId)
                .filter(account -> account.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BadCredentialsException("Account is unavailable"));
        return new UserResponse(user.getId(), user.getEmail(),
                user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()), user.getCreatedAt());
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }
        Role userRole = roles.findByName(RoleName.USER)
                .orElseThrow(() -> new IllegalStateException("Required USER role is missing"));
        User user = new User(email, passwordEncoder.encode(request.password()));
        user.addRole(userRole);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "uq_users_email".equals(violation.getConstraintName())) {
                    throw new EmailAlreadyExistsException();
                }
            }
            throw exception;
        }
        return new RegisterResponse(user.getId(), user.getEmail(), Set.of(RoleName.USER), user.getCreatedAt());
    }
}
