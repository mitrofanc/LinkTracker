package com.linktracker.application.service;

import com.linktracker.application.exception.LoginRateLimitException;
import com.linktracker.application.exception.LoginUnavailableException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptLimiter {
    private static final DefaultRedisScript<Long> CHECK = new DefaultRedisScript<>("""
            local wait = 0
            for i = 1, 2 do
                local count = tonumber(redis.call('GET', KEYS[i]) or '0')
                if count >= tonumber(ARGV[i]) then
                    wait = math.max(wait, redis.call('PTTL', KEYS[i]))
                end
            end
            return wait
            """, Long.class);
    private static final DefaultRedisScript<Long> FAILURE = new DefaultRedisScript<>("""
            for i = 1, 2 do
                local count = redis.call('INCR', KEYS[i])
                if count == 1 then
                    redis.call('PEXPIRE', KEYS[i], ARGV[1])
                end
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final int accountLimit;
    private final int ipLimit;
    private final Duration window;

    public LoginAttemptLimiter(StringRedisTemplate redis,
            @Value("${security.login.max-account-failures}") int accountLimit,
            @Value("${security.login.max-ip-failures}") int ipLimit,
            @Value("${security.login.window}") Duration window) {
        if (accountLimit < 1 || ipLimit < 1 || window.toMillis() < 1000) {
            throw new IllegalArgumentException("Login limits must be positive; window must be at least 1 second");
        }
        this.redis = redis;
        this.accountLimit = accountLimit;
        this.ipLimit = ipLimit;
        this.window = window;
    }

    public void check(String email, String ip) {
        try {
            Long wait = redis.execute(CHECK, keys(email, ip),
                    Integer.toString(accountLimit), Integer.toString(ipLimit));
            if (wait == null) {
                throw new LoginUnavailableException();
            }
            if (wait > 0) {
                throw new LoginRateLimitException((wait + 999) / 1000);
            }
        } catch (DataAccessException exception) {
            throw new LoginUnavailableException();
        }
    }

    public void failed(String email, String ip) {
        try {
            if (redis.execute(FAILURE, keys(email, ip), Long.toString(window.toMillis())) == null) {
                throw new LoginUnavailableException();
            }
        } catch (DataAccessException exception) {
            throw new LoginUnavailableException();
        }
    }

    public void succeeded(String email) {
        try {
            redis.delete(accountKey(email));
        } catch (DataAccessException exception) {
            throw new LoginUnavailableException();
        }
    }

    private static List<String> keys(String email, String ip) {
        return List.of(accountKey(email), "login:ip:" + hash(ip));
    }

    private static String accountKey(String email) {
        return "login:account:" + hash(email.trim().toLowerCase(Locale.ROOT));
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
