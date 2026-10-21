package com.linktracker.application.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.linktracker.application.model.Role;
import com.linktracker.application.model.RoleName;
import com.linktracker.application.model.User;
import com.linktracker.application.model.UserStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// Requires a separate empty PostgreSQL test database, never the application's working DB.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "spring.datasource.url=${TEST_DB_URL}",
    "spring.datasource.username=${TEST_DB_USERNAME}",
    "spring.datasource.password=${TEST_DB_PASSWORD}",
    "security.password.pepper=${TEST_PASSWORD_PEPPER}",
    "security.jwt.secret=${TEST_JWT_SECRET}"
})
@Transactional
class UserRepositoryTests {

    @Autowired
    private UserRepository users;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void persistsUserAndBothRolesAndReloadsFromDatabase() {
        Instant before = Instant.now().minusSeconds(1);
        User user = new User("student@example.test", "encoded-test-fixture");
        Role userRole = roles.findByName(RoleName.USER).orElseThrow();
        user.addRole(userRole);
        user.addRole(userRole);
        user.addRole(roles.findByName(RoleName.ADMIN).orElseThrow());
        users.saveAndFlush(user);
        Long id = user.getId();
        entityManager.clear();

        User loaded = users.findByEmail("student@example.test").orElseThrow();
        assertThat(loaded.getId()).isEqualTo(id).isPositive();
        assertThat(loaded.getPasswordHash()).isEqualTo("encoded-test-fixture");
        assertThat(loaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(loaded.getCreatedAt()).isBetween(before, Instant.now());
        assertThat(loaded.getRoles()).extracting(Role::getName)
                .containsExactlyInAnyOrder(RoleName.USER, RoleName.ADMIN);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_roles WHERE user_id = ?", Long.class, id))
                .isEqualTo(2L);
    }

    @Test
    void returnsEmptyForUnknownEmail() {
        assertThat(users.findByEmail("missing@example.test")).isEmpty();
        assertThat(users.existsByEmail("missing@example.test")).isFalse();
    }

    @Test
    void updatesStatusWithoutChangingCreationTime() {
        User user = users.saveAndFlush(new User("status@example.test", "encoded-test-fixture"));
        entityManager.clear();
        User loaded = users.findById(user.getId()).orElseThrow();
        Instant createdAt = loaded.getCreatedAt();
        loaded.setStatus(UserStatus.DISABLED);
        entityManager.flush();
        entityManager.clear();

        User updated = users.findById(user.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(users.existsByEmail("status@example.test")).isTrue();
    }

    @Test
    void databaseRejectsDuplicateEmail() {
        users.saveAndFlush(new User("duplicate@example.test", "encoded-test-fixture"));
        assertThatThrownBy(() -> users.saveAndFlush(new User("duplicate@example.test", "other-fixture")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsUnnormalizedEmail() {
        assertThatThrownBy(() -> users.saveAndFlush(new User(" Upper@example.test ", "encoded-test-fixture")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingUserRemovesAssignmentsButPreservesSharedRoles() {
        User user = new User("delete@example.test", "encoded-test-fixture");
        user.addRole(roles.findByName(RoleName.USER).orElseThrow());
        users.saveAndFlush(user);
        Long id = user.getId();
        entityManager.clear();

        users.deleteById(id);
        users.flush();
        entityManager.clear();

        assertThat(users.findById(id)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_roles WHERE user_id = ?", Long.class, id))
                .isZero();
        assertThat(roles.findByName(RoleName.USER)).isPresent();
        assertThat(roles.count()).isEqualTo(2L);
    }
}
