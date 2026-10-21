package com.linktracker.application.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
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
class SubscriptionSchemaTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void twoUsersCanSubscribeToOneSharedSource() {
        long source = source("spring-projects/spring-boot");
        long first = user("first@example.test");
        long second = user("second@example.test");
        long subscription = subscribe(first, source);
        subscribe(second, source);

        assertThat(subscription).isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sources", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM subscriptions WHERE source_id = ?",
                Long.class, source)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT created_at FROM subscriptions WHERE id = ?",
                OffsetDateTime.class, subscription)).isNotNull();
    }

    @Test
    void rejectsDuplicateSubscription() {
        long user = user("duplicate-subscription@example.test");
        long source = source("owner/repository");
        subscribe(user, source);

        assertThatThrownBy(() -> subscribe(user, source))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateExternalIdentityEvenWithDifferentUrl() {
        source("owner/repository");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO sources (type, canonical_url, external_id) VALUES ('GITHUB', ?, ?)",
                "https://github.com/owner/repository/", "owner/repository"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateCanonicalUrlEvenWithDifferentExternalIdentity() {
        source("owner/repository");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO sources (type, canonical_url, external_id) VALUES ('GITHUB', ?, ?)",
                "https://github.com/owner/repository", "another/repository"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsSubscriptionToMissingSource() {
        long user = user("missing-source@example.test");

        assertThatThrownBy(() -> subscribe(user, -1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsSubscriptionForMissingUser() {
        long source = source("owner/repository");

        assertThatThrownBy(() -> subscribe(-1, source))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingSubscriptionPreservesSharedSourceAndOtherSubscribers() {
        long source = source("owner/repository");
        long first = subscribe(user("delete-first@example.test"), source);
        long second = subscribe(user("keep-second@example.test"), source);

        jdbc.update("DELETE FROM subscriptions WHERE id = ?", first);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM sources WHERE id = ?",
                Long.class, source)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM subscriptions WHERE id = ?",
                Long.class, second)).isEqualTo(1L);
    }

    @Test
    void rejectsDeletingSubscribedSource() {
        long source = source("owner/repository");
        subscribe(user("keep-source@example.test"), source);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM sources WHERE id = ?", source))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDeletingSubscribedUser() {
        long user = user("keep-user@example.test");
        subscribe(user, source("owner/repository"));

        assertThatThrownBy(() -> jdbc.update("DELETE FROM users WHERE id = ?", user))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long user(String email) {
        return jdbc.queryForObject(
                "INSERT INTO users (email, password_hash) VALUES (?, 'test-fixture') RETURNING id",
                Long.class, email);
    }

    private long source(String externalId) {
        return jdbc.queryForObject(
                "INSERT INTO sources (type, canonical_url, external_id) VALUES ('GITHUB', ?, ?) RETURNING id",
                Long.class, "https://github.com/" + externalId, externalId);
    }

    private long subscribe(long userId, long sourceId) {
        return jdbc.queryForObject(
                "INSERT INTO subscriptions (user_id, source_id) VALUES (?, ?) RETURNING id",
                Long.class, userId, sourceId);
    }
}
