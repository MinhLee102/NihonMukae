package com.nihonmukae.backend.shadowing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihonmukae.backend.shadowing.ShadowingDtos.MarksRequest;
import com.nihonmukae.backend.shadowing.ShadowingDtos.MaterialDetail;
import com.nihonmukae.backend.shadowing.ShadowingDtos.MaterialRequest;
import com.nihonmukae.backend.shadowing.ShadowingDtos.MaterialSummary;
import com.nihonmukae.backend.shadowing.ShadowingDtos.Segment;
import com.nihonmukae.backend.shared.exception.NotFoundException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShadowingService {

    static final int MAX_MATERIALS_PER_USER = 50;
    static final int MAX_SEGMENTS = 600;
    static final int MAX_TEXT = 500;
    static final double MAX_SECONDS = 4 * 3600;
    private static final Pattern VIDEO_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ShadowingService(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<MaterialSummary> list(UUID userId) {
        return jdbc.query("""
                SELECT m.id, m.video_id, m.title, jsonb_array_length(m.segments) AS seg_count,
                       (SELECT count(*) FROM shadowing_marks k WHERE k.material_id = m.id) AS marked_count,
                       m.updated_at, m.last_practiced_at
                FROM shadowing_materials m WHERE m.user_id = :uid ORDER BY m.updated_at DESC
                """, new MapSqlParameterSource("uid", userId), (rs, i) -> new MaterialSummary(
                rs.getObject("id", UUID.class), rs.getString("video_id"), rs.getString("title"),
                rs.getInt("seg_count"), rs.getInt("marked_count"),
                rs.getObject("updated_at", OffsetDateTime.class), rs.getObject("last_practiced_at", OffsetDateTime.class)));
    }

    @Transactional(readOnly = true)
    public MaterialDetail get(UUID userId, UUID id) {
        MaterialDetail base = jdbc.query("""
                SELECT id, video_id, title, segments::text AS segments, last_practiced_at
                FROM shadowing_materials WHERE id = :id AND user_id = :uid
                """, new MapSqlParameterSource("id", id).addValue("uid", userId), rs -> {
            if (!rs.next()) {
                return null;
            }
            return new MaterialDetail(id, rs.getString("video_id"), rs.getString("title"),
                    parseSegments(rs.getString("segments")), List.of(), rs.getObject("last_practiced_at", OffsetDateTime.class));
        });
        if (base == null) {
            throw new NotFoundException("Không tìm thấy bài shadowing");
        }
        List<Integer> marked = jdbc.queryForList("SELECT seg_idx FROM shadowing_marks WHERE material_id = :id ORDER BY seg_idx",
                new MapSqlParameterSource("id", id), Integer.class);
        return new MaterialDetail(base.id(), base.videoId(), base.title(), base.segments(), marked, base.lastPracticedAt());
    }

    @Transactional
    public MaterialDetail create(UUID userId, MaterialRequest req) {
        String videoId = validVideoId(req.videoId());
        String title = validTitle(req.title());
        List<Segment> segments = validSegments(req.segments());
        Integer count = jdbc.queryForObject("SELECT count(*) FROM shadowing_materials WHERE user_id = :uid",
                new MapSqlParameterSource("uid", userId), Integer.class);
        if (count != null && count >= MAX_MATERIALS_PER_USER) {
            throw new IllegalArgumentException("Bạn đã có tối đa " + MAX_MATERIALS_PER_USER + " bài, hãy xoá bớt bài cũ");
        }
        UUID id = jdbc.queryForObject("""
                INSERT INTO shadowing_materials (user_id, video_id, title, segments)
                VALUES (:uid, :vid, :title, CAST(:seg AS jsonb)) RETURNING id
                """, new MapSqlParameterSource("uid", userId).addValue("vid", videoId).addValue("title", title)
                .addValue("seg", toJson(segments)), UUID.class);
        return get(userId, id);
    }

    @Transactional
    public MaterialDetail update(UUID userId, UUID id, MaterialRequest req) {
        String title = validTitle(req.title());
        List<Segment> segments = validSegments(req.segments());
        int n = jdbc.update("""
                UPDATE shadowing_materials SET title = :title, segments = CAST(:seg AS jsonb), updated_at = now()
                WHERE id = :id AND user_id = :uid
                """, new MapSqlParameterSource("id", id).addValue("uid", userId).addValue("title", title)
                .addValue("seg", toJson(segments)));
        if (n == 0) {
            throw new NotFoundException("Không tìm thấy bài shadowing");
        }
        // Câu bị bỏ khỏi phụ đề thì dấu của nó không còn nghĩa
        jdbc.update("DELETE FROM shadowing_marks WHERE material_id = :id AND seg_idx >= :n",
                new MapSqlParameterSource("id", id).addValue("n", segments.size()));
        return get(userId, id);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        int n = jdbc.update("DELETE FROM shadowing_materials WHERE id = :id AND user_id = :uid",
                new MapSqlParameterSource("id", id).addValue("uid", userId));
        if (n == 0) {
            throw new NotFoundException("Không tìm thấy bài shadowing");
        }
    }

    /** Ghi đè toàn bộ tập câu đã đánh dấu và cập nhật thời điểm luyện gần nhất. */
    @Transactional
    public List<Integer> saveMarks(UUID userId, UUID id, MarksRequest req) {
        Integer total = jdbc.query("SELECT jsonb_array_length(segments) FROM shadowing_materials WHERE id = :id AND user_id = :uid",
                new MapSqlParameterSource("id", id).addValue("uid", userId), rs -> rs.next() ? rs.getInt(1) : null);
        if (total == null) {
            throw new NotFoundException("Không tìm thấy bài shadowing");
        }
        TreeSet<Integer> marked = new TreeSet<>();
        for (Integer idx : req.marked() == null ? List.<Integer>of() : req.marked()) {
            if (idx != null && idx >= 0 && idx < total) {
                marked.add(idx);
            }
        }
        jdbc.update("DELETE FROM shadowing_marks WHERE material_id = :id", new MapSqlParameterSource("id", id));
        for (Integer idx : marked) {
            jdbc.update("INSERT INTO shadowing_marks (material_id, seg_idx) VALUES (:id, :i)",
                    new MapSqlParameterSource("id", id).addValue("i", idx));
        }
        jdbc.update("UPDATE shadowing_materials SET last_practiced_at = now() WHERE id = :id",
                new MapSqlParameterSource("id", id));
        return new ArrayList<>(marked);
    }

    // ── kiểm tra dữ liệu ────────────────────────────────────────────────

    static String validVideoId(String v) {
        if (v == null || !VIDEO_ID.matcher(v.trim()).matches()) {
            throw new IllegalArgumentException("Mã video YouTube không hợp lệ");
        }
        return v.trim();
    }

    static String validTitle(String t) {
        String s = t == null ? "" : t.trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("Tiêu đề không được để trống");
        }
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    static List<Segment> validSegments(List<Segment> in) {
        if (in == null || in.isEmpty()) {
            throw new IllegalArgumentException("Cần ít nhất một câu phụ đề");
        }
        if (in.size() > MAX_SEGMENTS) {
            throw new IllegalArgumentException("Phụ đề tối đa " + MAX_SEGMENTS + " câu");
        }
        List<Segment> out = new ArrayList<>(in.size());
        double prevStart = -1;
        for (Segment s : in) {
            String text = s == null || s.text() == null ? "" : s.text().trim();
            if (text.isEmpty() || text.length() > MAX_TEXT) {
                throw new IllegalArgumentException("Mỗi câu phải có nội dung và dài tối đa " + MAX_TEXT + " ký tự");
            }
            double start = s.start();
            double end = s.end();
            if (!(start >= 0) || !(end > start) || end > MAX_SECONDS || start < prevStart) {
                throw new IllegalArgumentException("Mốc thời gian không hợp lệ ở câu \"" + abbreviate(text) + "\"");
            }
            prevStart = start;
            out.add(new Segment(round(start), round(end), text));
        }
        return out;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String abbreviate(String s) {
        return s.length() > 20 ? s.substring(0, 20) + "…" : s;
    }

    private String toJson(List<Segment> segments) {
        try {
            return mapper.writeValueAsString(segments);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<Segment> parseSegments(String json) {
        try {
            return mapper.readValue(json, new TypeReference<List<Segment>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
