package com.linktracker.application.service;

import com.linktracker.application.dto.TokenResponse;
import com.linktracker.application.model.User;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final String issuer;
    private final Duration ttl;

    public JwtService(JwtEncoder encoder,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.ttl}") Duration ttl) {
        if (ttl.toSeconds() < 1 || ttl.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("JWT TTL must be between 1 second and 24 hours");
        }
        this.encoder = encoder;
        this.issuer = issuer;
        this.ttl = ttl;
    }

    public TokenResponse issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("roles", user.getRoles().stream().map(role -> role.getName().name()).sorted().toList())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", ttl.toSeconds());
    }
}
