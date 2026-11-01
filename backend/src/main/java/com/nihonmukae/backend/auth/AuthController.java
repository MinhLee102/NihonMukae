package com.nihonmukae.backend.auth;

import com.nihonmukae.backend.shared.entity.User;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // true khi chạy sau HTTPS (production): trình duyệt chỉ gửi cookie refresh qua kết nối mã hóa
    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    private static final String REFRESH_COOKIE_NAME = "refresh_token";

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req, HttpServletResponse res) {
        User u = authService.register(req.getEmail(), req.getPassword(), req.getName());
        String access = authService.generateAccessToken(u);
        String rawRefresh = authService.createAndStoreRefreshToken(u);
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, rawRefresh)
                .httpOnly(true)
                        .secure(cookieSecure)
                        .path("/")
                        .maxAge(Duration.ofDays(30))
                        .sameSite("Lax")
                        .build();
                res.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(Map.of("accessToken", access));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpServletResponse res) {
        return authService.validateUserCredentials(req.getEmail(), req.getPassword())
                .map(user -> {
                    String access = authService.generateAccessToken(user);
                    String rawRefresh = authService.createAndStoreRefreshToken(user);
                    ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, rawRefresh)
                            .httpOnly(true)
                            .secure(cookieSecure)
                            .path("/")
                            .maxAge(Duration.ofDays(30))
                            .sameSite("Lax")
                            .build();
                    res.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());
                    return ResponseEntity.ok(Map.of("accessToken", access));
                }).orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid_credentials")));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest req, HttpServletResponse res) {
        jakarta.servlet.http.Cookie[] cookies = req.getCookies();
        if (cookies == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "no_refresh_token"));
        String raw = null;
        for (jakarta.servlet.http.Cookie c : cookies) {
            if (REFRESH_COOKIE_NAME.equals(c.getName())) {
                raw = c.getValue();
                break;
            }
        }
        if (raw == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "no_refresh_token"));

        var optUser = authService.refreshFromToken(raw);
        if (optUser.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid_refresh_token"));
        User user = optUser.get();
        String access = authService.generateAccessToken(user);
        // createAndStoreRefreshToken already rotated a new token in service; but we need to send latest token to client.
        // For simplicity, create another new token here and store+send it (service rotation already created one, but it's fine to create a fresh one now).
        String rawRefresh = authService.createAndStoreRefreshToken(user);
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, rawRefresh)
                .httpOnly(true)
                        .secure(cookieSecure)
                .path("/")
                .maxAge(Duration.ofDays(30))
                .sameSite("Lax")
                .build();
        res.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(Map.of("accessToken", access));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest req, HttpServletResponse res) {
        jakarta.servlet.http.Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie c : cookies) {
                if (REFRESH_COOKIE_NAME.equals(c.getName())) {
                    authService.revokeRefreshToken(c.getValue());
                    break;
                }
            }
        }
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                        .secure(cookieSecure)
                .path("/")
                .maxAge(Duration.ofSeconds(0))
                .sameSite("Lax")
                .build();
        res.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @Data
    static class RegisterRequest {
        private String email;
        private String password;
        private String name;
    }

    @Data
    static class LoginRequest {
        private String email;
        private String password;
    }
}
