package com.linktracker.application.controller;

import com.linktracker.application.dto.CreateSubscriptionRequest;
import com.linktracker.application.dto.SubscriptionResponse;
import com.linktracker.application.service.SubscriptionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptions;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateSubscriptionRequest request) {
        return subscriptions.create(Long.parseLong(jwt.getSubject()), request.url());
    }

    @GetMapping
    public List<SubscriptionResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return subscriptions.list(Long.parseLong(jwt.getSubject()));
    }
}
