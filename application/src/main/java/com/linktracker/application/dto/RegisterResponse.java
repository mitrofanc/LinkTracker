package com.linktracker.application.dto;

import com.linktracker.application.model.RoleName;
import java.time.Instant;
import java.util.Set;

public record RegisterResponse(Long id, String email, Set<RoleName> roles, Instant createdAt) {
}
