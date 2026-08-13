package com.nihonmukae.backend.auth;

import com.nihonmukae.backend.auth.jwt.JwtUtil;
import com.nihonmukae.backend.shared.entity.RefreshToken;
import com.nihonmukae.backend.shared.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // Refresh token lifetime (days)
    private static final int REFRESH_TOKEN_EXPIRE_DAYS = 30;

    public User register(String email, String rawPassword, String name) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already in use");
        }
        User u = User.builder()
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .name(name)
                .provider("local")
                .role("USER")
                .build();
        return userRepository.save(u);
    }

    public String generateAccessToken(User user) {
        return jwtUtil.generateAccessToken(user);
    }

    public String createAndStoreRefreshToken(User user) {
        // generate strong random token (UUID + random bytes)
        String raw = UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString();
        String hashed = sha256Base64Url(raw);

        RefreshToken rt = RefreshToken.builder()
                .user(user)
                .token(hashed)
                .expiresAt(ZonedDateTime.now().plusDays(REFRESH_TOKEN_EXPIRE_DAYS))
                .build();
        refreshTokenRepository.save(rt);
        return raw; // return original token to be sent to client
    }

    public Optional<User> validateUserCredentials(String email, String rawPassword) {
        Optional<User> o = userRepository.findByEmail(email);
        if (o.isEmpty()) return Optional.empty();
        User u = o.get();
        if (u.getPassword() == null) return Optional.empty();
        if (passwordEncoder.matches(rawPassword, u.getPassword())) return Optional.of(u);
        return Optional.empty();
    }

    public Optional<User> refreshFromToken(String rawToken) {
        String hashed = sha256Base64Url(rawToken);
        Optional<RefreshToken> o = refreshTokenRepository.findByTokenAndExpiresAtAfter(hashed, ZonedDateTime.now());
        if (o.isEmpty()) return Optional.empty();
        // rotate: remove old and create new
        RefreshToken old = o.get();
        User user = old.getUser();
        refreshTokenRepository.deleteByToken(hashed);
        createAndStoreRefreshToken(user);
        return Optional.of(user);
    }

    public void revokeRefreshToken(String rawToken) {
        String hashed = sha256Base64Url(rawToken);
        refreshTokenRepository.deleteByToken(hashed);
    }

    private String sha256Base64Url(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
