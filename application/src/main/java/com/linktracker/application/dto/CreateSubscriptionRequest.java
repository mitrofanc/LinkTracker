package com.linktracker.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSubscriptionRequest(@NotBlank @Size(max = 2048) String url) {
}
