package com.linktracker.application.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.linktracker.application.model.User;
import com.linktracker.application.model.UserStatus;
import com.linktracker.application.repository.UserRepository;
import com.linktracker.application.service.JwtService;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=${TEST_DB_URL}",
    "spring.datasource.username=${TEST_DB_USERNAME}",
    "spring.datasource.password=${TEST_DB_PASSWORD}",
    "security.password.pepper=${TEST_PASSWORD_PEPPER}",
    "security.jwt.secret=${TEST_JWT_SECRET}"
})
@AutoConfigureMockMvc
class SubscriptionTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private JwtService jwt;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanTestDatabase() {
        jdbc.update("DELETE FROM subscriptions");
        jdbc.update("DELETE FROM sources");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void createsSubscriptionAndListsItInSeparateRequest() throws Exception {
        String token = token(user("owner@example.test"));
        mvc.perform(post("/subscriptions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\" HTTPS://GITHUB.COM/Owner/Repository.git/ \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.source.id").isNumber())
                .andExpect(jsonPath("$.source.type").value("GITHUB"))
                .andExpect(jsonPath("$.source.canonicalUrl").value("https://github.com/owner/repository"))
                .andExpect(jsonPath("$.source.externalId").value("owner/repository"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.user").doesNotExist());

        mvc.perform(get("/subscriptions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].source.externalId").value("owner/repository"));
    }

    @Test
    void listsOnlyCurrentUserAndIgnoresSuppliedOwner() throws Exception {
        User first = user("first@example.test");
        User second = user("second@example.test");
        String firstToken = token(first);
        String secondToken = token(second);
        mvc.perform(post("/subscriptions").header("Authorization", "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://github.com/owner/repository\",\"userId\":"
                                + second.getId() + "}"))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT user_id FROM subscriptions", Long.class)).isEqualTo(first.getId());
        mvc.perform(get("/subscriptions").param("userId", first.getId().toString())
                        .header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/subscriptions").header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void duplicateNormalizedUrlReturnsConflict() throws Exception {
        String token = token(user("owner@example.test"));
        assertThat(create(token, "https://github.com/owner/repository")).isEqualTo(201);
        mvc.perform(post("/subscriptions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://github.com/Owner/Repository.git/\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_ALREADY_EXISTS"));
        assertCounts(1, 1);
    }

    @Test
    void twoUsersShareOneSource() throws Exception {
        assertThat(create(token(user("first@example.test")), "https://github.com/owner/repository"))
                .isEqualTo(201);
        assertThat(create(token(user("second@example.test")), "https://github.com/Owner/Repository.git"))
                .isEqualTo(201);
        assertCounts(1, 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}", "{\"url\":null}", "{\"url\":\" \"}",
        "{\"url\":\"https://gitlab.com/owner/repo\"}",
        "{\"url\":\"https://github.com/owner/repo/issues/1\"}",
        "{\"url\":\"not a URL\"}", "{\"url\":\"https://user:secret@github.com/owner/repo\"}",
        "{broken"
    })
    void rejectsInvalidInputWithoutSavingAnything(String body) throws Exception {
        String token = token(user("owner@example.test"));
        mvc.perform(post("/subscriptions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").isNotEmpty());
        assertCounts(0, 0);
    }

    @Test
    void rejectsOverlongUrl() throws Exception {
        String token = token(user("owner@example.test"));
        mvc.perform(post("/subscriptions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://github.com/owner/" + "x".repeat(2048) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertCounts(0, 0);
    }

    @Test
    void bothOperationsRequireAuthentication() throws Exception {
        mvc.perform(get("/subscriptions")).andExpect(status().isUnauthorized());
        mvc.perform(post("/subscriptions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://github.com/owner/repo\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAccountCannotUseExistingToken() throws Exception {
        User user = user("disabled@example.test");
        String token = token(user);
        user.setStatus(UserStatus.DISABLED);
        users.saveAndFlush(user);
        assertThat(create(token, "https://github.com/owner/repo")).isEqualTo(401);
        mvc.perform(get("/subscriptions").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        assertCounts(0, 0);
    }

    @Test
    void deletedAccountCannotUseExistingToken() throws Exception {
        User user = user("deleted@example.test");
        String token = token(user);
        users.deleteById(user.getId());
        assertThat(create(token, "https://github.com/owner/repo")).isEqualTo(401);
        mvc.perform(get("/subscriptions").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        assertCounts(0, 0);
    }

    @Test
    void concurrentDifferentUsersCreateOneSharedSource() throws Exception {
        String first = token(user("first@example.test"));
        String second = token(user("second@example.test"));
        assertThat(concurrentCreate(first, second)).containsExactlyInAnyOrder(201, 201);
        assertCounts(1, 2);
    }

    @Test
    void concurrentSameUserGetsOneSuccessAndOneConflict() throws Exception {
        String token = token(user("owner@example.test"));
        assertThat(concurrentCreate(token, token)).containsExactlyInAnyOrder(201, 409);
        assertCounts(1, 1);
    }

    private List<Integer> concurrentCreate(String first, String second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> one = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return create(first, "https://github.com/owner/repository");
            };
            Callable<Integer> two = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return create(second, "https://github.com/Owner/Repository.git/");
            };
            var a = executor.submit(one);
            var b = executor.submit(two);
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
    }

    private int create(String token, String url) throws Exception {
        return mvc.perform(post("/subscriptions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"" + url + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    private User user(String email) {
        return users.saveAndFlush(new User(email, "encoded-test-fixture"));
    }

    private String token(User user) {
        return jwt.issue(user).accessToken();
    }

    private void assertCounts(long sources, long subscriptions) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sources", Long.class)).isEqualTo(sources);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM subscriptions", Long.class)).isEqualTo(subscriptions);
    }
}
