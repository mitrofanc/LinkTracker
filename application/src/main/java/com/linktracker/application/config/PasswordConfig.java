package com.linktracker.application.config;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class PasswordConfig {

    @Bean
    PasswordEncoder passwordEncoder(@Value("${security.password.pepper}") String pepper) {
        if (pepper.isBlank() || pepper.length() < 32) {
            throw new IllegalArgumentException("PASSWORD_PEPPER must contain at least 32 characters");
        }
        return new Pbkdf2PasswordEncoder(
                pepper, 16, 310_000,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
    }
}
