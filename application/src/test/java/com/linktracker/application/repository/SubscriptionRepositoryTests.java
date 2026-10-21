package com.linktracker.application.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.linktracker.application.model.Source;
import com.linktracker.application.model.SourceType;
import com.linktracker.application.model.Subscription;
import com.linktracker.application.model.User;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

// Requires a separate PostgreSQL test database, never the application's working DB.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "spring.datasource.url=${TEST_DB_URL}",
    "spring.datasource.username=${TEST_DB_USERNAME}",
    "spring.datasource.password=${TEST_DB_PASSWORD}",
    "security.password.pepper=${TEST_PASSWORD_PEPPER}",
    "security.jwt.secret=${TEST_JWT_SECRET}"
})
@Transactional
class SubscriptionRepositoryTests {

    @Autowired
    private SourceRepository sources;
    @Autowired
    private SubscriptionRepository subscriptions;
    @Autowired
    private UserRepository users;
    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsSubscriptionAndReloadsItsSourceAndOwner() {
        User user = user("owner@example.test");
        Source source = source("owner/repository");
        Instant before = Instant.now().minusSeconds(1);
        Subscription subscription = subscriptions.saveAndFlush(new Subscription(user, source));
        Long id = subscription.getId();
        entityManager.clear();

        Subscription loaded = subscriptions.findByIdAndUserId(id, user.getId()).orElseThrow();
        assertThat(loaded.getId()).isPositive();
        assertThat(loaded.getCreatedAt()).isBetween(before, Instant.now());
        assertThat(loaded.getUser().getId()).isEqualTo(user.getId());
        assertThat(loaded.getSource().getId()).isEqualTo(source.getId());
        assertThat(loaded.getSource().getType()).isEqualTo(SourceType.GITHUB);
        assertThat(loaded.getSource().getCanonicalUrl()).isEqualTo("https://github.com/owner/repository");
        assertThat(loaded.getSource().getExternalId()).isEqualTo("owner/repository");
        assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(loaded, "source")).isTrue();
    }

    @Test
    void findsSharedSourceByProviderAndExternalId() {
        Source source = source("owner/repository");
        entityManager.clear();

        assertThat(sources.findByTypeAndExternalId(SourceType.GITHUB, "owner/repository"))
                .get().extracting(Source::getId).isEqualTo(source.getId());
        assertThat(sources.findByTypeAndExternalId(SourceType.GITHUB, "missing/repository")).isEmpty();
    }

    @Test
    void listsOnlyOwnersSubscriptionsInStableOrderWithSourcesLoaded() {
        User first = user("first@example.test");
        User second = user("second@example.test");
        User empty = user("empty@example.test");
        Source shared = source("owner/shared");
        Subscription firstSubscription = subscriptions.saveAndFlush(new Subscription(first, shared));
        subscriptions.saveAndFlush(new Subscription(second, shared));
        Subscription nextSubscription = subscriptions.saveAndFlush(new Subscription(first, source("owner/next")));
        entityManager.clear();

        var result = subscriptions.findAllByUserIdOrderByIdAsc(first.getId());
        assertThat(result).extracting(Subscription::getId)
                .containsExactly(firstSubscription.getId(), nextSubscription.getId());
        assertThat(result).allSatisfy(subscription -> {
            assertThat(subscription.getUser().getId()).isEqualTo(first.getId());
            assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
                    .isLoaded(subscription, "source")).isTrue();
        });
        assertThat(subscriptions.findAllByUserIdOrderByIdAsc(second.getId()))
                .hasSize(1).first().extracting(subscription -> subscription.getSource().getId())
                .isEqualTo(shared.getId());
        assertThat(subscriptions.findAllByUserIdOrderByIdAsc(empty.getId())).isEmpty();
        assertThat(sources.count()).isEqualTo(2);
    }

    @Test
    void ownerScopedLookupDoesNotReturnAnotherUsersSubscription() {
        User owner = user("owner@example.test");
        User other = user("other@example.test");
        Source source = source("owner/repository");
        Subscription subscription = subscriptions.saveAndFlush(new Subscription(owner, source));
        entityManager.clear();

        assertThat(subscriptions.findByIdAndUserId(subscription.getId(), other.getId())).isEmpty();
        assertThat(subscriptions.findByIdAndUserId(-1L, owner.getId())).isEmpty();
        assertThat(subscriptions.existsByUserIdAndSourceId(owner.getId(), source.getId())).isTrue();
        assertThat(subscriptions.existsByUserIdAndSourceId(other.getId(), source.getId())).isFalse();
    }

    @Test
    void rejectsDuplicateSubscriptionThroughJpa() {
        User user = user("duplicate@example.test");
        Source source = source("owner/repository");
        subscriptions.saveAndFlush(new Subscription(user, source));

        assertThatThrownBy(() -> subscriptions.saveAndFlush(new Subscription(user, source)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingSubscriptionDoesNotCascadeToSharedSourceOrUsers() {
        User first = user("first@example.test");
        User second = user("second@example.test");
        Source shared = source("owner/shared");
        Subscription deleted = subscriptions.saveAndFlush(new Subscription(first, shared));
        Subscription kept = subscriptions.saveAndFlush(new Subscription(second, shared));
        entityManager.clear();

        subscriptions.deleteById(deleted.getId());
        subscriptions.flush();
        entityManager.clear();

        assertThat(subscriptions.findById(deleted.getId())).isEmpty();
        assertThat(subscriptions.findByIdAndUserId(kept.getId(), second.getId())).isPresent();
        assertThat(sources.findById(shared.getId())).isPresent();
        assertThat(users.findById(first.getId())).isPresent();
        assertThat(users.findById(second.getId())).isPresent();
    }

    private User user(String email) {
        return users.saveAndFlush(new User(email, "encoded-test-fixture"));
    }

    private Source source(String externalId) {
        return sources.saveAndFlush(new Source(SourceType.GITHUB,
                "https://github.com/" + externalId, externalId));
    }
}
