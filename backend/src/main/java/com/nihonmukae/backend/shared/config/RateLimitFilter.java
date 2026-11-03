package com.nihonmukae.backend.shared.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Slows down password guessing and mass sign-ups (per client IP) and bulk imports. Caddy replaces any
 * X-Forwarded-For sent by the client with the real address, and the backend is only reachable through Caddy.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long MINUTE = 60_000L;

    private final RateLimiter login = new RateLimiter(10, MINUTE);
    private final RateLimiter register = new RateLimiter(5, 10 * MINUTE);
    private final RateLimiter refresh = new RateLimiter(60, MINUTE);
    private final RateLimiter imports = new RateLimiter(30, MINUTE);

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        RateLimiter limiter = limiterFor(request);
        if (limiter != null && !limiter.tryAcquire(clientIp(request), System.currentTimeMillis())) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json;charset=UTF-8");
            response.getOutputStream().write(
                    "{\"success\":false,\"error\":\"too_many_requests\",\"message\":\"Thao tác quá nhanh, vui lòng thử lại sau ít phút.\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(request, response);
    }

    private RateLimiter limiterFor(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return null;
        }
        String path = request.getRequestURI();
        return switch (path) {
            case "/auth/login" -> login;
            case "/auth/register" -> register;
            case "/auth/refresh" -> refresh;
            case "/questions/import/preview", "/questions/import/commit" -> imports;
            default -> null;
        };
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
