package com.linktracker.application.exception;

public class SubscriptionAlreadyExistsException extends RuntimeException {

    public SubscriptionAlreadyExistsException() {
        super("You are already subscribed to this source");
    }
}
