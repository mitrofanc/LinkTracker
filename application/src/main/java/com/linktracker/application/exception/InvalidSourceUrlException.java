package com.linktracker.application.exception;

public class InvalidSourceUrlException extends RuntimeException {

    public InvalidSourceUrlException() {
        super("Expected a GitHub repository URL: https://github.com/owner/repository");
    }
}
