package com.linktracker.application.dto;

import com.linktracker.application.model.SourceType;

public record SourceResponse(Long id, SourceType type, String canonicalUrl, String externalId) {
}
