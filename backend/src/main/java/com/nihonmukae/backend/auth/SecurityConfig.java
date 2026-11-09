package com.nihonmukae.backend.auth;

import com.nihonmukae.backend.auth.jwt.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    // Reads CORS_ALLOWED_ORIGIN from docker-compose env (Spring relaxed
    // binding maps CORS_ALLOWED_ORIGIN -> cors.allowed-origin automatically).
    // Falls back to localhost:3000 if not set.
    @Value("${cors.allowed-origin:http://localhost:3000}")
    private String allowedOrigin;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Token thiếu hoặc hết hạn phải trả 401 (mặc định Spring trả 403), vì frontend chỉ tự refresh khi gặp 401
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        // Khu quản trị và nạp đề: chỉ ADMIN (role trong JWT, nên nâng quyền xong phải đăng nhập lại)
                        // MODERATOR (chưa gán cho ai) duyệt góp ý và sửa câu hỏi nhưng không quản lý người dùng hay server
                        .requestMatchers(HttpMethod.DELETE, "/admin/questions/hidden").hasRole("ADMIN")
                        .requestMatchers("/admin/feedback/**", "/admin/questions/**", "/admin/dictionary/**")
                        .hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers("/admin/**", "/questions/import/**").hasRole("ADMIN")
                        // Audio và ảnh của đề thi: thẻ audio/img của trình duyệt không gửi được header Authorization
                        .requestMatchers(HttpMethod.GET, "/media/**").permitAll()
                        .requestMatchers(
                                "/auth/**",
                                "/health",
                                "/dictionary/search",
                                "/dictionary/lookup",
                                "/actuator/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/error"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // REQUIRED so the browser sends/receives the HttpOnly refresh_token
        // cookie cross-origin. Cannot be combined with allowedOrigins("*") —
        // Spring throws at startup if you try, by design (spec forbids it).
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration conf) throws Exception {
        return conf.getAuthenticationManager();
    }
}
