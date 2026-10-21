package com.linktracker.application.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.linktracker.application.model.Role;
import com.linktracker.application.model.RoleName;
import com.linktracker.application.model.User;
import com.linktracker.application.repository.UserRepository;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

// TEST_DB_* must point to an isolated test database: these tests commit HTTP requests.
@SpringBootTest(properties = {
    "spring.datasource.url=${TEST_DB_URL}",
    "spring.datasource.username=${TEST_DB_USERNAME}",
    "spring.datasource.password=${TEST_DB_PASSWORD}",
    "security.password.pepper=${TEST_PASSWORD_PEPPER}",
    "security.jwt.secret=${TEST_JWT_SECRET}"
})
@AutoConfigureMockMvc
class RegistrationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder encoder;

    @AfterEach
    void removeTestUsers() {
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void registersWithoutAuthenticationAndPersistsOnlyUserRole() throws Exception {
        String password = "test-password-123";
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"  Student@Example.test  ","password":"test-password-123","roles":["ADMIN"]}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("student@example.test"))
                .andExpect(jsonPath("$.roles[0]").value("USER"))
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User user = users.findByEmail("student@example.test").orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo(password).startsWith("{pbkdf2}");
        assertThat(encoder.matches(password, user.getPasswordHash())).isTrue();
        assertThat(encoder.matches("wrong-password", user.getPasswordHash())).isFalse();
        assertThat(user.getRoles()).extracting(Role::getName).containsExactly(RoleName.USER);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_roles", Long.class)).isEqualTo(1L);
    }

    @Test
    void duplicateNormalizedEmailReturnsConflict() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body("duplicate@example.test", "test-password-123"))).andExpect(status().isCreated());
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body("DUPLICATE@example.test", "test-password-123")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
        assertThat(users.count()).isEqualTo(1L);
    }

    @Test
    void samePasswordGetsDifferentSaltAndPasswordSpacesArePreserved() throws Exception {
        String password = "  test-password-123  ";
        for (String email : List.of("first@example.test", "second@example.test")) {
            mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content(body(email, password))).andExpect(status().isCreated());
        }
        String first = users.findByEmail("first@example.test").orElseThrow().getPasswordHash();
        String second = users.findByEmail("second@example.test").orElseThrow().getPasswordHash();
        assertThat(first).isNotEqualTo(second);
        assertThat(encoder.matches(password, first)).isTrue();
        assertThat(encoder.matches(password.trim(), first)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}",
        "{\"email\":\"invalid\",\"password\":\"test-password-123\"}",
        "{\"email\":\"user@example.test\",\"password\":\"1234567\"}",
        "{\"email\":\"user@example.test\",\"password\":\"        \"}",
        "{\"email\":null,\"password\":\"test-password-123\"}",
        "{\"email\":\"user@example.test\",\"password\":null}"
    })
    void invalidFieldsReturnBadRequestWithoutSaving(String body) throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
        assertThat(users.count()).isZero();
    }

    @Test
    void rejectsOverlongPassword() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body("user@example.test", "a".repeat(129))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
        assertThat(users.count()).isZero();
    }

    @Test
    void malformedJsonReturnsSafeError() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JSON"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void unrelatedEndpointRemainsClosed() throws Exception {
        mvc.perform(get("/private")).andExpect(status().isUnauthorized());
    }

    @Test
    void simultaneousRegistrationsCreateOnlyOneAccount() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var task = (java.util.concurrent.Callable<Integer>) () -> {
                start.await();
                return mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body("race@example.test", "test-password-123")))
                        .andReturn().getResponse().getStatus();
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(users.count()).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_roles", Long.class)).isEqualTo(1L);
    }

    private static String body(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }
}
