package com.nihonmukae.backend.kaiwa;

import com.nihonmukae.backend.shared.exception.NotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class KaiwaService {

    static final int MAX_SESSIONS_PER_USER = 200;
    static final int MAX_SUMMARY = 3000;

    public record SaveRequest(Integer level, String topic, String summary, Integer turns, OffsetDateTime startedAt) {
    }

    public record SessionDto(UUID id, int level, String topic, String summary, int turns,
                             OffsetDateTime startedAt, OffsetDateTime endedAt) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public KaiwaService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SessionDto> list(UUID userId) {
        return jdbc.query("""
                SELECT id, level, topic, summary, turns, started_at, ended_at
                FROM kaiwa_sessions WHERE user_id = :uid ORDER BY ended_at DESC LIMIT 100
                """, new MapSqlParameterSource("uid", userId), (rs, i) -> new SessionDto(
                rs.getObject("id", UUID.class), rs.getInt("level"), rs.getString("topic"), rs.getString("summary"),
                rs.getInt("turns"), rs.getObject("started_at", OffsetDateTime.class), rs.getObject("ended_at", OffsetDateTime.class)));
    }

    public SessionDto save(UUID userId, SaveRequest req) {
        int level = req.level() == null ? 0 : req.level();
        if (level < 1 || level > 5) {
            throw new IllegalArgumentException("Cấp độ phải từ N1 đến N5");
        }
        String topic = clean(req.topic(), 200);
        String summary = clean(req.summary(), MAX_SUMMARY);
        if (topic.isEmpty() || summary.isEmpty()) {
            throw new IllegalArgumentException("Cần có chủ đề và tóm tắt");
        }
        Integer count = jdbc.queryForObject("SELECT count(*) FROM kaiwa_sessions WHERE user_id = :uid",
                new MapSqlParameterSource("uid", userId), Integer.class);
        if (count != null && count >= MAX_SESSIONS_PER_USER) {
            throw new IllegalArgumentException("Bạn đã lưu tối đa " + MAX_SESSIONS_PER_USER + " buổi, hãy xoá bớt buổi cũ");
        }
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime started = req.startedAt() == null || req.startedAt().isAfter(now) ? now : req.startedAt();
        int turns = Math.max(0, Math.min(req.turns() == null ? 0 : req.turns(), 1000));
        UUID id = jdbc.queryForObject("""
                INSERT INTO kaiwa_sessions (user_id, level, topic, summary, turns, started_at, ended_at)
                VALUES (:uid, :level, :topic, :summary, :turns, :started, :ended) RETURNING id
                """, new MapSqlParameterSource("uid", userId).addValue("level", level).addValue("topic", topic)
                .addValue("summary", summary).addValue("turns", turns).addValue("started", started).addValue("ended", now),
                UUID.class);
        return new SessionDto(id, level, topic, summary, turns, started, now);
    }

    public void delete(UUID userId, UUID id) {
        int n = jdbc.update("DELETE FROM kaiwa_sessions WHERE id = :id AND user_id = :uid",
                new MapSqlParameterSource("id", id).addValue("uid", userId));
        if (n == 0) {
            throw new NotFoundException("Không tìm thấy buổi hội thoại");
        }
    }

    static String clean(String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
