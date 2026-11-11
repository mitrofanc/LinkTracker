package com.linktracker.application.exception;

public class LoginUnavailableException extends RuntimeException {
    public LoginUnavailableException() {
        super("Login is temporarily unavailable. Try again later");
    }
}
