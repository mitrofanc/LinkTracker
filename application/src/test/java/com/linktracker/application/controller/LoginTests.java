package com.linktracker.application.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.linktracker.application.dto.LoginRequest;
import com.linktracker.application.dto.TokenResponse;
import com.linktracker.application.model.RoleName;
import com.linktracker.application.model.User;
import com.linktracker.application.model.UserStatus;
import com.linktracker.application.repository.RoleRepository;
import com.linktracker.application.repository.UserRepository;
import com.linktracker.application.service.AuthService;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=${TEST_DB_URL}",
    "spring.datasource.username=${TEST_DB_USERNAME}",
    "spring.datasource.password=${TEST_DB_PASSWORD}",
    "security.password.pepper=${TEST_PASSWORD_PEPPER}",
    "security.jwt.secret=${TEST_JWT_SECRET}"
})
@AutoConfigureMockMvc
class LoginTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private PasswordEncoder passwords;
    @Autowired
    private AuthService auth;
    @Autowired
    private JwtEncoder encoder;
    @Autowired
    private JwtDecoder decoder;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanTestDatabase() {
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM users");
    }

    @Test
    void loginIssuesSignedTokenAndMeReturnsCurrentAccount() throws Exception {
        User user = createUser();
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":" STUDENT@EXAMPLE.TEST ","password":"test-password-123"}
                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.password").doesNotExist());

        String token = auth.login(new LoginRequest(user.getEmail(), "test-password-123")).accessToken();
        var jwt = decoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("linktracker");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(jwt.getClaims()).doesNotContainKeys("password", "passwordHash", "email");
        mvc.perform(get("/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.roles[0]").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void wrongPasswordAndUnknownEmailReturnSameSafeError() throws Exception {
        createUser();
        String first = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"student@example.test","password":"wrong-password"}
                """))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"unknown@example.test","password":"wrong-password"}
                """))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(first).isEqualTo(second).contains("INVALID_CREDENTIALS");
    }

    @Test
    void disabledAccountCannotLogin() throws Exception {
        User user = createUser();
        user.setStatus(UserStatus.DISABLED);
        users.saveAndFlush(user);
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"student@example.test","password":"test-password-123"}
                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meRejectsDisabledAndDeletedAccountsEvenWithValidToken() throws Exception {
        User user = createUser();
        String token = auth.login(new LoginRequest(user.getEmail(), "test-password-123")).accessToken();
        user.setStatus(UserStatus.DISABLED);
        users.saveAndFlush(user);
        mvc.perform(get("/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        users.deleteById(user.getId());
        mvc.perform(get("/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingMalformedAndTamperedTokensAreUnauthorized() throws Exception {
        mvc.perform(get("/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        User user = createUser();
        String token = auth.login(new LoginRequest(user.getEmail(), "test-password-123")).accessToken();
        String[] parts = token.split("\\.");
        parts[2] = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        mvc.perform(get("/me").header("Authorization", "Bearer " + String.join(".", parts)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredWrongIssuerAndFutureTokensAreUnauthorized() throws Exception {
        User user = createUser();
        Instant now = Instant.now();
        String expired = signed(user, "linktracker", now.minusSeconds(120), now.minusSeconds(60));
        String wrongIssuer = signed(user, "other-app", now, now.plusSeconds(60));
        JwtClaimsSet future = JwtClaimsSet.builder().issuer("linktracker").subject(user.getId().toString())
                .issuedAt(now).notBefore(now.plusSeconds(120)).expiresAt(now.plusSeconds(3600)).build();
        String futureToken = encode(future);
        for (String token : new String[]{expired, wrongIssuer, futureToken}) {
            mvc.perform(get("/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void missingExpiryAndInvalidSubjectAreUnauthorized() throws Exception {
        for (JwtClaimsSet claims : new JwtClaimsSet[]{
                JwtClaimsSet.builder().issuer("linktracker").subject("1").build(),
                JwtClaimsSet.builder().issuer("linktracker").subject("not-an-id")
                        .expiresAt(Instant.now().plusSeconds(60)).build()}) {
            mvc.perform(get("/me").header("Authorization", "Bearer " + encode(claims)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void userIsForbiddenFromAdminPathAndAdminPassesRoleGuard() throws Exception {
        User user = createUser();
        String userToken = auth.login(new LoginRequest(user.getEmail(), "test-password-123")).accessToken();
        mvc.perform(get("/admin/users").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
        // No admin controller exists yet: 404 proves the request passed the role guard.
        jdbc.update("INSERT INTO user_roles(user_id, role_id) SELECT ?, id FROM roles WHERE name = 'ADMIN'",
                user.getId());
        String adminToken = auth.login(new LoginRequest(user.getEmail(), "test-password-123")).accessToken();
        mvc.perform(get("/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void validatesLoginAndDoesNotExposeCredentialsInToString() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalid\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
        assertThat(new LoginRequest("student@example.test", "secret-password").toString())
                .doesNotContain("secret-password");
        assertThat(new TokenResponse("secret-token", "Bearer", 3600).toString())
                .doesNotContain("secret-token");
    }

    private User createUser() {
        User user = new User("student@example.test", passwords.encode("test-password-123"));
        user.addRole(roles.findByName(RoleName.USER).orElseThrow());
        return users.saveAndFlush(user);
    }

    private String signed(User user, String issuer, Instant issuedAt, Instant expiresAt) {
        return encode(JwtClaimsSet.builder().issuer(issuer).subject(user.getId().toString())
                .issuedAt(issuedAt).expiresAt(expiresAt).claim("roles", java.util.List.of("USER")).build());
    }

    private String encode(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
