package com.linktracker.application.dto;

import java.time.Instant;

public record SubscriptionResponse(Long id, SourceResponse source, Instant createdAt) {
}
