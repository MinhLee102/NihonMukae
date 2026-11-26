package com.nihonmukae.backend.grammar;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihonmukae.backend.grammar.GrammarValidator.Content;
import com.nihonmukae.backend.grammar.GrammarValidator.Result;
import com.nihonmukae.backend.shared.exception.NotFoundException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Soạn ngữ pháp trên trang Admin, hai bước: (1) lưu danh sách tên mẫu theo cấp (AI liệt kê hoặc trích từ file
 * tham khảo), (2) lấy từng batch mẫu chưa có nội dung để AI viết, kiểm tra rồi lưu kết quả.
 */
@Service
public class AdminGrammarService {

    public static final int DEFAULT_BATCH = 15;
    private static final int MAX_BATCH = 30;
    private static final int MAX_LIST = 400;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AdminGrammarService(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public record LevelSummary(int level, int total, int ready, int pending, int invalid) {
    }

    public record PointRow(long id, String pattern, String reading, String category, String hint, boolean ready,
                           boolean invalid) {
    }

    public record ListItem(String pattern, String reading, String category, String hint) {
    }

    public record ListImport(Integer level, List<ListItem> items) {
    }

    public record ListResult(int added, int skipped, List<String> problems) {
    }

    /** Một mục đưa vào prompt: id dạng "G123" để AI trả lại đúng mục. */
    public record BatchItem(String id, String level, String pattern, String reading, String category, String hint) {
    }

    public record Issue(String id, String pattern, List<String> messages) {
    }

    public record ContentResult(int saved, int invalid, List<Issue> errors, List<Issue> warnings) {
    }

    // ─── Tổng quan và danh sách ────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<LevelSummary> summary() {
        Map<Integer, LevelSummary> byLevel = new HashMap<>();
        jdbc.query("""
                SELECT level, count(*) AS total,
                       count(*) FILTER (WHERE meaning IS NOT NULL) AS ready,
                       count(*) FILTER (WHERE meaning IS NULL AND NOT invalid) AS pending,
                       count(*) FILTER (WHERE invalid) AS invalid
                FROM grammar_points GROUP BY level
                """, rs -> {
            int level = rs.getInt("level");
            byLevel.put(level, new LevelSummary(level, rs.getInt("total"), rs.getInt("ready"), rs.getInt("pending"),
                    rs.getInt("invalid")));
        });
        List<LevelSummary> out = new ArrayList<>();
        for (int level = 5; level >= 1; level--) {
            out.add(byLevel.getOrDefault(level, new LevelSummary(level, 0, 0, 0, 0)));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<PointRow> points(int level) {
        checkLevel(level);
        return jdbc.query("""
                SELECT id, pattern, reading, category, hint, meaning IS NOT NULL AS ready, invalid
                FROM grammar_points WHERE level = :level ORDER BY sort_order, id
                """, new MapSqlParameterSource("level", level),
                (rs, i) -> new PointRow(rs.getLong("id"), rs.getString("pattern"), rs.getString("reading"),
                        rs.getString("category"), rs.getString("hint"), rs.getBoolean("ready"), rs.getBoolean("invalid")));
    }

    // ─── Bước 1: danh sách tên mẫu ─────────────────────────────────────

    /** Thêm các mẫu chưa có (so theo tên đã chuẩn hoá) vào cuối danh sách của cấp đó. */
    @Transactional
    public ListResult importList(ListImport req) {
        int level = req.level() == null ? 0 : req.level();
        checkLevel(level);
        List<ListItem> items = req.items() == null ? List.of() : req.items();
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Danh sách trống");
        }
        if (items.size() > MAX_LIST) {
            throw new IllegalArgumentException("Tối đa " + MAX_LIST + " mẫu mỗi lần");
        }
        MapSqlParameterSource lp = new MapSqlParameterSource("level", level);
        Set<String> existing = new HashSet<>();
        for (String p : jdbc.queryForList("SELECT pattern FROM grammar_points WHERE level = :level", lp, String.class)) {
            existing.add(GrammarValidator.normPattern(p));
        }
        Integer max = jdbc.queryForObject("SELECT COALESCE(max(sort_order), -1) FROM grammar_points WHERE level = :level",
                lp, Integer.class);
        int order = (max == null ? -1 : max) + 1;
        int added = 0;
        int skipped = 0;
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ListItem it = items.get(i);
            String pattern = it == null || it.pattern() == null ? "" : it.pattern().trim();
            if (pattern.isEmpty()) {
                problems.add("Mục " + (i + 1) + ": thiếu pattern");
                continue;
            }
            if (pattern.length() > 100) {
                problems.add("Mục " + (i + 1) + " (" + pattern.substring(0, 20) + "…): tên mẫu quá dài");
                continue;
            }
            if (!existing.add(GrammarValidator.normPattern(pattern))) {
                skipped++;
                continue;
            }
            jdbc.update("""
                    INSERT INTO grammar_points (level, pattern, reading, category, hint, sort_order)
                    VALUES (:level, :pattern, :reading, :category, :hint, :order)
                    """, new MapSqlParameterSource("level", level).addValue("pattern", pattern)
                    .addValue("reading", trimTo(it.reading(), 100)).addValue("category", trimTo(it.category(), 60))
                    .addValue("hint", trimTo(it.hint(), 300)).addValue("order", order++));
            added++;
        }
        return new ListResult(added, skipped, problems);
    }

    // ─── Bước 2: nội dung ──────────────────────────────────────────────

    /** Các mẫu kế tiếp chưa có nội dung (bỏ mẫu AI đã đánh dấu invalid), theo thứ tự danh sách. */
    @Transactional(readOnly = true)
    public List<BatchItem> nextBatch(int level, Integer size) {
        checkLevel(level);
        int n = size == null || size <= 0 ? DEFAULT_BATCH : Math.min(size, MAX_BATCH);
        return jdbc.query("""
                SELECT id, level, pattern, reading, category, hint FROM grammar_points
                WHERE level = :level AND meaning IS NULL AND NOT invalid
                ORDER BY sort_order, id LIMIT :n
                """, new MapSqlParameterSource("level", level).addValue("n", n),
                (rs, i) -> new BatchItem("G" + rs.getLong("id"), "N" + rs.getInt("level"), rs.getString("pattern"),
                        rs.getString("reading"), rs.getString("category"), rs.getString("hint")));
    }

    /** Kiểm tra và lưu câu trả lời của AI (mảng JSON). Mục lỗi không lưu, mẫu đó vẫn chờ ở batch sau. */
    @Transactional
    public ContentResult saveContent(List<Map<String, Object>> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("Không có mục nào trong JSON");
        }
        if (entries.size() > MAX_BATCH * 4) {
            throw new IllegalArgumentException("Quá nhiều mục một lần");
        }
        int saved = 0;
        int invalid = 0;
        List<Issue> errors = new ArrayList<>();
        List<Issue> warnings = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            Map<String, Object> e = entries.get(i);
            String rawId = e == null || e.get("id") == null ? "" : String.valueOf(e.get("id")).trim();
            Long id = parseId(rawId);
            if (id == null) {
                errors.add(new Issue(rawId.isEmpty() ? "mục " + (i + 1) : rawId, "", List.of("id không hợp lệ (cần dạng G123)")));
                continue;
            }
            if (!seen.add(id)) {
                errors.add(new Issue(rawId, "", List.of("id bị lặp, chỉ lấy mục đầu tiên")));
                continue;
            }
            List<Map<String, Object>> found = jdbc.queryForList(
                    "SELECT pattern, reading FROM grammar_points WHERE id = :id", new MapSqlParameterSource("id", id));
            if (found.isEmpty()) {
                errors.add(new Issue(rawId, "", List.of("không có mẫu này (có thể đã bị xoá)")));
                continue;
            }
            String pattern = (String) found.get(0).get("pattern");
            String reading = (String) found.get(0).get("reading");
            if (Boolean.TRUE.equals(e.get("invalid"))) {
                jdbc.update("UPDATE grammar_points SET invalid = true, updated_at = now() WHERE id = :id",
                        new MapSqlParameterSource("id", id));
                invalid++;
                continue;
            }
            Result r = GrammarValidator.validate(e, pattern, reading);
            if (!r.warnings().isEmpty()) {
                warnings.add(new Issue(rawId, pattern, r.warnings()));
            }
            if (r.content() == null) {
                errors.add(new Issue(rawId, pattern, r.errors()));
                continue;
            }
            store(id, r.content());
            saved++;
        }
        return new ContentResult(saved, invalid, errors, warnings);
    }

    private void store(long id, Content c) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", id).addValue("reading", trimTo(c.reading(), 100))
                .addValue("connection", json(c.connection())).addValue("meaning", c.meaning())
                .addValue("explanation", c.explanation()).addValue("notes", c.notes().isEmpty() ? null : c.notes())
                .addValue("related", json(c.similar()));
        jdbc.update("""
                UPDATE grammar_points SET reading = COALESCE(:reading, reading), connection = CAST(:connection AS jsonb),
                    meaning = :meaning, explanation = :explanation, notes = :notes, related = CAST(:related AS jsonb),
                    invalid = false, updated_at = now()
                WHERE id = :id
                """, p);
        jdbc.update("DELETE FROM grammar_examples WHERE point_id = :id", p);
        for (int i = 0; i < c.examples().size(); i++) {
            jdbc.update("INSERT INTO grammar_examples (point_id, idx, ja, vi) VALUES (:id, :idx, :ja, :vi)",
                    new MapSqlParameterSource("id", id).addValue("idx", i).addValue("ja", c.examples().get(i).ja())
                            .addValue("vi", c.examples().get(i).vi()));
        }
    }

    @Transactional
    public void delete(long id) {
        if (jdbc.update("DELETE FROM grammar_points WHERE id = :id", new MapSqlParameterSource("id", id)) == 0) {
            throw new NotFoundException("Không tìm thấy mẫu ngữ pháp");
        }
    }

    /** Cho mẫu từng bị AI đánh dấu invalid vào lại hàng chờ. */
    @Transactional
    public void restore(long id) {
        if (jdbc.update("UPDATE grammar_points SET invalid = false, updated_at = now() WHERE id = :id",
                new MapSqlParameterSource("id", id)) == 0) {
            throw new NotFoundException("Không tìm thấy mẫu ngữ pháp");
        }
    }

    static Long parseId(String raw) {
        if (raw == null || !raw.matches("[Gg]?\\d{1,12}")) {
            return null;
        }
        return Long.parseLong(raw.replaceFirst("^[Gg]", ""));
    }

    private static void checkLevel(int level) {
        if (level < 1 || level > 5) {
            throw new IllegalArgumentException("Cấp độ phải từ 1 đến 5");
        }
    }

    private static String trimTo(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
