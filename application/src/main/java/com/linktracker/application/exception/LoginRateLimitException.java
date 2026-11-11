package com.linktracker.application.exception;

public class LoginRateLimitException extends RuntimeException {
    private final long retryAfterSeconds;

    public LoginRateLimitException(long retryAfterSeconds) {
        super("Too many login attempts. Try again later");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
