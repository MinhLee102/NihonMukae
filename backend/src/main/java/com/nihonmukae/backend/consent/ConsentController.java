package com.nihonmukae.backend.consent;

import com.nihonmukae.backend.auth.jwt.JwtUserPrincipal;
import com.nihonmukae.backend.shared.dto.ApiResponse;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ghi nhận và rút lại sự đồng ý của người dùng. Phiên bản hiện hành của từng loại do server giữ:
 * đồng ý ở phiên bản cũ không còn hiệu lực khi nội dung đồng ý thay đổi.
 */
@RestController
@RequestMapping("/consents")
public class ConsentController {

    /** kind → phiên bản nội dung đồng ý hiện hành. Tăng số khi đổi nội dung để hỏi lại mọi người. */
    static final Map<String, Integer> CURRENT = Map.of("kaiwa", 2);

    public record Status(String kind, int currentVersion, boolean accepted, OffsetDateTime acceptedAt) {
    }

    public record AcceptRequest(Integer version) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public ConsentController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/{kind}")
    public ApiResponse<Status> status(@AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable String kind) {
        return ApiResponse.success(read(principal.getId(), kind));
    }

    @PostMapping("/{kind}")
    public ApiResponse<Status> accept(@AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable String kind,
                                      @RequestBody AcceptRequest req) {
        int current = currentVersion(kind);
        if (req.version() == null || req.version() != current) {
            throw new IllegalArgumentException("Nội dung đồng ý đã thay đổi, hãy tải lại trang và đọc lại");
        }
        jdbc.update("""
                INSERT INTO user_consents (user_id, kind, version) VALUES (:uid, :kind, :v)
                ON CONFLICT (user_id, kind) DO UPDATE SET version = :v, accepted_at = now()
                """, new MapSqlParameterSource("uid", principal.getId()).addValue("kind", kind).addValue("v", current));
        return ApiResponse.success(read(principal.getId(), kind));
    }

    @DeleteMapping("/{kind}")
    public ApiResponse<Status> withdraw(@AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable String kind) {
        currentVersion(kind);
        jdbc.update("DELETE FROM user_consents WHERE user_id = :uid AND kind = :kind",
                new MapSqlParameterSource("uid", principal.getId()).addValue("kind", kind));
        return ApiResponse.success(read(principal.getId(), kind));
    }

    private Status read(UUID userId, String kind) {
        int current = currentVersion(kind);
        return jdbc.query("SELECT version, accepted_at FROM user_consents WHERE user_id = :uid AND kind = :kind",
                new MapSqlParameterSource("uid", userId).addValue("kind", kind), rs -> {
                    if (rs.next() && rs.getInt("version") == current) {
                        return new Status(kind, current, true, rs.getObject("accepted_at", OffsetDateTime.class));
                    }
                    return new Status(kind, current, false, null);
                });
    }

    private static int currentVersion(String kind) {
        Integer v = CURRENT.get(kind);
        if (v == null) {
            throw new IllegalArgumentException("Loại đồng ý không hợp lệ");
        }
        return v;
    }
}
