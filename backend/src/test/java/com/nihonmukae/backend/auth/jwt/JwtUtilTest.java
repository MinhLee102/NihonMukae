package com.nihonmukae.backend.auth.jwt;

import com.nihonmukae.backend.shared.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-key-min-256-bits-for-hs256-algorithm!!");
        properties.setAccessTokenExpirationMinutes(15);

        jwtUtil = new JwtUtil(properties);
        jwtUtil.init();
    }

    @Test
    void generateAccessToken_containsExpectedClaims() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("learner@example.com")
                .role("USER")
                .build();

        String token = jwtUtil.generateAccessToken(user);

        assertThat(jwtUtil.isTokenValid(token)).isTrue();
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(userId);
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("learner@example.com");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("USER");
        assertThat(jwtUtil.extractExpiration(token))
                .isAfter(Instant.now().plus(14, ChronoUnit.MINUTES));
    }

    @Test
    void isTokenValid_returnsFalseForTamperedToken() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("learner@example.com")
                .role("USER")
                .build();

        String token = jwtUtil.generateAccessToken(user);
        // Sửa một ký tự ở giữa chữ ký. Ký tự cuối của base64url có bit thừa nên đổi nó đôi khi không đổi chữ ký.
        int middle = token.lastIndexOf('.') + 10;
        char replacement = token.charAt(middle) == 'A' ? 'B' : 'A';
        String tamperedToken = token.substring(0, middle) + replacement + token.substring(middle + 1);

        assertThat(jwtUtil.isTokenValid(tamperedToken)).isFalse();
    }

    @Test
    void isTokenValid_returnsFalseForMalformedToken() {
        assertThat(jwtUtil.isTokenValid("not-a-jwt")).isFalse();
    }
}
