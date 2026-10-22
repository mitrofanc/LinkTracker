package com.linktracker.application.service;

import com.linktracker.application.dto.SourceResponse;
import com.linktracker.application.dto.SubscriptionResponse;
import com.linktracker.application.exception.SubscriptionAlreadyExistsException;
import com.linktracker.application.model.Source;
import com.linktracker.application.model.Subscription;
import com.linktracker.application.model.User;
import com.linktracker.application.model.UserStatus;
import com.linktracker.application.repository.SourceRepository;
import com.linktracker.application.repository.SubscriptionRepository;
import com.linktracker.application.repository.UserRepository;
import com.linktracker.application.source.ParsedSource;
import com.linktracker.application.source.SourceUrlResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SourceUrlResolver resolver;
    private final SourceRepository sources;
    private final SubscriptionRepository subscriptions;
    private final UserRepository users;

    @Transactional
    public SubscriptionResponse create(long userId, String url) {
        User user = activeUser(userId);
        ParsedSource parsed = resolver.resolve(url);
        Source source = sources.findByTypeAndExternalId(parsed.type(), parsed.externalId())
                .orElseGet(() -> {
                    sources.insertIfAbsent(parsed.type().name(), parsed.canonicalUrl(), parsed.externalId());
                    return sources.findByTypeAndExternalId(parsed.type(), parsed.externalId()).orElseThrow();
                });
        if (subscriptions.existsByUserIdAndSourceId(userId, source.getId())) {
            throw new SubscriptionAlreadyExistsException();
        }
        Subscription subscription = new Subscription(user, source);
        try {
            subscriptions.saveAndFlush(subscription);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "uq_subscriptions_user_source".equals(violation.getConstraintName())) {
                    throw new SubscriptionAlreadyExistsException();
                }
            }
            throw exception;
        }
        return response(subscription);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> list(long userId) {
        activeUser(userId);
        return subscriptions.findAllByUserIdOrderByIdAsc(userId).stream()
                .map(SubscriptionService::response)
                .toList();
    }

    private User activeUser(long userId) {
        return users.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BadCredentialsException("Account is unavailable"));
    }

    private static SubscriptionResponse response(Subscription subscription) {
        Source source = subscription.getSource();
        return new SubscriptionResponse(subscription.getId(),
                new SourceResponse(source.getId(), source.getType(), source.getCanonicalUrl(), source.getExternalId()),
                subscription.getCreatedAt());
    }
}
