package com.linktracker.application;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Full context against explicitly configured, isolated PostgreSQL and Redis.
@SpringBootTest(properties = {
    "spring.datasource.url=${TEST_DB_URL}",
    "spring.datasource.username=${TEST_DB_USERNAME}",
    "spring.datasource.password=${TEST_DB_PASSWORD}",
    "security.password.pepper=${TEST_PASSWORD_PEPPER}",
    "security.jwt.secret=${TEST_JWT_SECRET}",
    "spring.data.redis.host=${TEST_REDIS_HOST}",
    "spring.data.redis.port=${TEST_REDIS_PORT}"
})
class ApplicationTests {
    @Test
    void contextLoads() {
    }
}
