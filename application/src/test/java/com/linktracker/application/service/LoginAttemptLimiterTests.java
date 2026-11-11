package com.linktracker.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.linktracker.application.exception.LoginRateLimitException;
import com.linktracker.application.exception.LoginUnavailableException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class LoginAttemptLimiterTests {
    private static LettuceConnectionFactory connection;
    private static StringRedisTemplate redis;

    @BeforeAll
    static void connectToIsolatedRedis() {
        connection = new LettuceConnectionFactory(System.getenv("TEST_REDIS_HOST"),
                Integer.parseInt(System.getenv("TEST_REDIS_PORT")));
        connection.afterPropertiesSet();
        connection.start();
        redis = new StringRedisTemplate(connection);
    }

    @AfterEach
    void clearCounters() {
        var keys = redis.keys("login:*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
    }

    @AfterAll
    static void disconnect() {
        connection.destroy();
    }

    @Test
    void accountLimitIsNormalizedAndIndependentOfIp() {
        var limiter = new LoginAttemptLimiter(redis, 2, 10, Duration.ofMinutes(15));
        limiter.failed(" User@Example.test ", "ip-a");
        limiter.check("user@example.test", "ip-a");
        limiter.failed("user@example.test", "ip-b");
        assertThatThrownBy(() -> limiter.check("USER@example.test", "ip-c"))
                .isInstanceOf(LoginRateLimitException.class);
        limiter.check("another@example.test", "ip-c");
    }

    @Test
    void ipLimitAppliesAcrossAccountsAndSuccessDoesNotClearIt() {
        var limiter = new LoginAttemptLimiter(redis, 10, 2, Duration.ofMinutes(15));
        limiter.failed("first@example.test", "ip-a");
        limiter.failed("second@example.test", "ip-a");
        limiter.succeeded("first@example.test");
        assertThatThrownBy(() -> limiter.check("third@example.test", "ip-a"))
                .isInstanceOf(LoginRateLimitException.class);
        limiter.check("third@example.test", "ip-b");
    }

    @Test
    void successClearsOnlyAccountFailures() {
        var limiter = new LoginAttemptLimiter(redis, 2, 10, Duration.ofMinutes(15));
        limiter.failed("first@example.test", "ip-a");
        limiter.succeeded("first@example.test");
        limiter.failed("first@example.test", "ip-a");
        limiter.check("first@example.test", "ip-a");
    }

    @Test
    void countersExpireAndLoginBecomesAvailable() throws Exception {
        var limiter = new LoginAttemptLimiter(redis, 1, 1, Duration.ofSeconds(1));
        limiter.failed("first@example.test", "ip-a");
        assertThatThrownBy(() -> limiter.check("first@example.test", "ip-a"))
                .isInstanceOf(LoginRateLimitException.class);
        for (String key : redis.keys("login:*")) {
            assertThat(redis.getExpire(key)).isBetween(0L, 1L);
        }
        Thread.sleep(1100);
        limiter.check("first@example.test", "ip-a");
        assertThat(redis.keys("login:*")).isEmpty();
    }

    @Test
    void concurrentFailuresAreCountedAtomicallyAndAlwaysHaveTtl() throws Exception {
        var limiter = new LoginAttemptLimiter(redis, 100, 100, Duration.ofMinutes(15));
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 40; i++) {
                futures.add(executor.submit(() -> limiter.failed("first@example.test", "ip-a")));
            }
            for (var future : futures) future.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        var keys = redis.keys("login:*");
        assertThat(keys).hasSize(2);
        for (String key : keys) {
            assertThat(redis.opsForValue().get(key)).isEqualTo("40");
            assertThat(redis.getExpire(key)).isPositive();
            assertThat(key).doesNotContain("first@example.test", "ip-a");
        }
    }

    @Test
    void unavailableRedisFailsClosed() throws Exception {
        try (var unusedPort = new ServerSocket(0)) {
            var config = new org.springframework.data.redis.connection.RedisStandaloneConfiguration(
                    "127.0.0.1", unusedPort.getLocalPort());
            unusedPort.close();
            var client = org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration.builder()
                    .commandTimeout(Duration.ofMillis(200)).build();
            var unavailable = new LettuceConnectionFactory(config, client);
            unavailable.afterPropertiesSet();
            unavailable.start();
            try {
                var limiter = new LoginAttemptLimiter(new StringRedisTemplate(unavailable), 2, 10,
                        Duration.ofMinutes(15));
                assertThatThrownBy(() -> limiter.check("user@example.test", "ip"))
                        .isInstanceOf(LoginUnavailableException.class);
            } finally {
                unavailable.destroy();
            }
        }
    }
}
