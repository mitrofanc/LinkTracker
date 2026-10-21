package com.linktracker.application.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    @Override
    public String toString() {
        return "TokenResponse[accessToken=REDACTED]";
    }
}
