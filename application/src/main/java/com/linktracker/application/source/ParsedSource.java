package com.linktracker.application.source;

import com.linktracker.application.model.SourceType;

public record ParsedSource(SourceType type, String canonicalUrl, String externalId) {
}
