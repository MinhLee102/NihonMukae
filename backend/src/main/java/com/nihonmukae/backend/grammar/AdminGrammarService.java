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
                           boolean invalid, List<Integer> extraLevels) {
    }

    public record ListItem(String pattern, String reading, String category, String hint) {
    }

    public record ListImport(Integer level, List<ListItem> items) {
    }

    /** crossLevel: các mẫu mới thêm trùng tên với mẫu ở cấp khác (xem mục Trùng giữa các cấp). */
    public record ListResult(int added, int skipped, List<String> problems, List<String> crossLevel) {
    }

    /**
     * Một mục đưa vào prompt: id dạng "G123" để AI trả lại đúng mục. level ghi cả các cấp phụ ("N4, cũng dùng ở N3").
     * note: lưu ý cho AI khi mẫu trùng tên với mẫu ở cấp khác nhưng khác nghĩa.
     */
    public record BatchItem(String id, String level, String pattern, String reading, String category, String hint,
                            String note) {
    }

    /** Một mẫu trong nhóm trùng giữa các cấp. */
    public record DuplicateRow(long id, int level, List<Integer> extraLevels, String pattern, String reading,
                               String meaning) {
    }

    public record MergeRequest(Long keepId, List<Long> mergeIds) {
    }

    public record IdsRequest(List<Long> ids) {
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
                SELECT id, pattern, reading, category, hint, meaning IS NOT NULL AS ready, invalid, extra_levels
                FROM grammar_points WHERE level = :level ORDER BY sort_order, id
                """, new MapSqlParameterSource("level", level),
                (rs, i) -> new PointRow(rs.getLong("id"), rs.getString("pattern"), rs.getString("reading"),
                        rs.getString("category"), rs.getString("hint"), rs.getBoolean("ready"), rs.getBoolean("invalid"),
                        levels(rs.getArray("extra_levels"))));
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
        Map<String, Integer> otherLevels = new HashMap<>();
        jdbc.query("SELECT level, pattern FROM grammar_points WHERE level <> :level", lp,
                rs -> {
                    otherLevels.putIfAbsent(GrammarDuplicates.patternKey(rs.getString("pattern")), rs.getInt("level"));
                });
        List<String> crossLevel = new ArrayList<>();
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
            Integer other = otherLevels.get(GrammarDuplicates.patternKey(pattern));
            if (other != null) {
                crossLevel.add(pattern + " (đã có ở N" + other + ")");
            }
            jdbc.update("""
                    INSERT INTO grammar_points (level, pattern, reading, category, hint, sort_order)
                    VALUES (:level, :pattern, :reading, :category, :hint, :order)
                    """, new MapSqlParameterSource("level", level).addValue("pattern", pattern)
                    .addValue("reading", trimTo(it.reading(), 100)).addValue("category", trimTo(it.category(), 60))
                    .addValue("hint", trimTo(it.hint(), 300)).addValue("order", order++));
            added++;
        }
        return new ListResult(added, skipped, problems, crossLevel);
    }

    // ─── Bước 2: nội dung ──────────────────────────────────────────────

    /** Các mẫu kế tiếp chưa có nội dung (bỏ mẫu AI đã đánh dấu invalid), theo thứ tự danh sách. */
    @Transactional(readOnly = true)
    public List<BatchItem> nextBatch(int level, Integer size) {
        checkLevel(level);
        int n = size == null || size <= 0 ? DEFAULT_BATCH : Math.min(size, MAX_BATCH);
        return pending("level = :level", new MapSqlParameterSource("level", level).addValue("n", n));
    }

    /** Mọi mẫu chưa có nội dung (cả một cấp, hoặc mọi cấp khi level null), để giao cho agent chạy một lượt. */
    @Transactional(readOnly = true)
    public List<BatchItem> allPending(Integer level) {
        MapSqlParameterSource p = new MapSqlParameterSource("n", 5000);
        if (level == null) {
            return pending("TRUE", p);
        }
        checkLevel(level);
        return pending("level = :level", p.addValue("level", level));
    }

    private List<BatchItem> pending(String where, MapSqlParameterSource p) {
        // where chỉ là hằng số trong lớp này ("TRUE" hoặc "level = :level"), không nhận chuỗi từ người dùng
        List<BatchItem> items = jdbc.query("SELECT id, level, extra_levels, pattern, reading, category, hint"
                + " FROM grammar_points WHERE " + where + " AND meaning IS NULL AND NOT invalid"
                + " ORDER BY level DESC, sort_order, id LIMIT :n", p, (rs, i) -> {
            List<Integer> extra = levels(rs.getArray("extra_levels"));
            String lv = "N" + rs.getInt("level") + (extra.isEmpty() ? ""
                    : ", cũng dùng ở " + String.join(", ", extra.stream().map(x -> "N" + x).toList()));
            return new BatchItem("G" + rs.getLong("id"), lv, rs.getString("pattern"), rs.getString("reading"),
                    rs.getString("category"), rs.getString("hint"), null);
        });
        if (items.isEmpty()) {
            return items;
        }
        // Mẫu đã được giữ riêng với một mẫu cùng tên ở cấp khác: dặn AI chỉ viết nghĩa của cấp này
        List<Long> ids = items.stream().map(b -> Long.parseLong(b.id().substring(1))).toList();
        Map<Long, List<String>> siblings = new HashMap<>();
        jdbc.query("""
                SELECT d.a AS self, o.level, o.pattern FROM grammar_distinct d JOIN grammar_points o ON o.id = d.b
                WHERE d.a IN (:ids)
                UNION ALL
                SELECT d.b AS self, o.level, o.pattern FROM grammar_distinct d JOIN grammar_points o ON o.id = d.a
                WHERE d.b IN (:ids)
                """, new MapSqlParameterSource("ids", ids), rs -> {
            siblings.computeIfAbsent(rs.getLong("self"), k -> new ArrayList<>())
                    .add(rs.getString("pattern") + " ở N" + rs.getInt("level"));
        });
        List<BatchItem> out = new ArrayList<>();
        for (BatchItem b : items) {
            List<String> sib = siblings.get(Long.parseLong(b.id().substring(1)));
            out.add(sib == null ? b : new BatchItem(b.id(), b.level(), b.pattern(), b.reading(), b.category(), b.hint(),
                    "Mẫu cùng tên " + String.join("; ", sib) + " mang nghĩa khác và được soạn riêng. Chỉ viết nghĩa, "
                            + "cách dùng và ví dụ thuộc cấp " + b.level().split(",")[0] + "."));
        }
        return out;
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

    // ─── Trùng giữa các cấp ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<List<DuplicateRow>> duplicates() {
        List<GrammarDuplicates.Point> points = new ArrayList<>();
        Map<Long, DuplicateRow> rows = new HashMap<>();
        jdbc.query("SELECT id, level, extra_levels, pattern, reading, meaning FROM grammar_points", rs -> {
            long id = rs.getLong("id");
            points.add(new GrammarDuplicates.Point(id, rs.getInt("level"), rs.getString("pattern"), rs.getString("reading")));
            rows.put(id, new DuplicateRow(id, rs.getInt("level"), levels(rs.getArray("extra_levels")),
                    rs.getString("pattern"), rs.getString("reading"), rs.getString("meaning")));
        });
        Set<Long> distinct = new HashSet<>();
        jdbc.query("SELECT a, b FROM grammar_distinct", rs -> {
            distinct.add(GrammarDuplicates.pairKey(rs.getLong("a"), rs.getLong("b")));
        });
        List<List<DuplicateRow>> out = new ArrayList<>();
        for (List<GrammarDuplicates.Point> g : GrammarDuplicates.groups(points, distinct)) {
            out.add(g.stream().map(p -> rows.get(p.id())).toList());
        }
        return out;
    }

    /**
     * Gộp các mẫu trùng vào một: giữ mẫu keepId (nội dung của nó, hoặc của mẫu khác nếu nó chưa có), cấp chính là
     * cấp thấp nhất trong nhóm (N5 thấp hơn N4), các cấp còn lại vào extra_levels. Các mẫu kia bị xoá.
     */
    @Transactional
    public void merge(MergeRequest req) {
        if (req.keepId() == null || req.mergeIds() == null || req.mergeIds().isEmpty()) {
            throw new IllegalArgumentException("Thiếu mẫu cần gộp");
        }
        List<Long> others = req.mergeIds().stream().filter(id -> !id.equals(req.keepId())).distinct().toList();
        if (others.isEmpty()) {
            throw new IllegalArgumentException("Thiếu mẫu cần gộp");
        }
        List<Long> all = new ArrayList<>(others);
        all.add(req.keepId());
        record Row(long id, int level, List<Integer> extra, int sortOrder, boolean ready) {
        }
        Map<Long, Row> rows = new HashMap<>();
        jdbc.query("SELECT id, level, extra_levels, sort_order, meaning IS NOT NULL AS ready FROM grammar_points WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", all), rs -> {
                    rows.put(rs.getLong("id"), new Row(rs.getLong("id"), rs.getInt("level"), levels(rs.getArray("extra_levels")),
                            rs.getInt("sort_order"), rs.getBoolean("ready")));
                });
        if (rows.size() != all.size()) {
            throw new NotFoundException("Có mẫu không còn tồn tại, hãy tải lại trang");
        }
        Row keep = rows.get(req.keepId());
        MapSqlParameterSource kp = new MapSqlParameterSource("keep", keep.id());
        if (!keep.ready()) {
            Row source = others.stream().map(rows::get).filter(Row::ready).findFirst().orElse(null);
            if (source != null) {
                kp.addValue("src", source.id());
                jdbc.update("""
                        UPDATE grammar_points k SET reading = COALESCE(k.reading, s.reading), connection = s.connection,
                            meaning = s.meaning, explanation = s.explanation, notes = s.notes, related = s.related
                        FROM grammar_points s WHERE k.id = :keep AND s.id = :src
                        """, kp);
                jdbc.update("DELETE FROM grammar_examples WHERE point_id = :keep", kp);
                jdbc.update("UPDATE grammar_examples SET point_id = :keep WHERE point_id = :src", kp);
            }
        }
        Row main = rows.values().stream()
                .max((a, b) -> a.level() != b.level() ? Integer.compare(a.level(), b.level())
                        : Boolean.compare(a.id() == keep.id(), b.id() == keep.id()))
                .orElse(keep);
        java.util.TreeSet<Integer> levels = new java.util.TreeSet<>(java.util.Comparator.reverseOrder());
        for (Row r : rows.values()) {
            levels.add(r.level());
            levels.addAll(r.extra());
        }
        levels.remove(main.level());
        jdbc.update("DELETE FROM grammar_points WHERE id IN (:ids)", new MapSqlParameterSource("ids", others));
        try {
            jdbc.update("""
                    UPDATE grammar_points SET level = :level, extra_levels = CAST(:extra AS smallint[]),
                        sort_order = :sort, invalid = false, updated_at = now() WHERE id = :keep
                    """, kp.addValue("level", main.level()).addValue("sort", main.sortOrder())
                    .addValue("extra", "{" + String.join(",", levels.stream().map(String::valueOf).toList()) + "}"));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Cấp N" + main.level() + " đã có một mẫu khác cùng tên. Xoá hoặc gộp mẫu đó trước.");
        }
    }

    /** Đánh dấu các mẫu là khác nghĩa, giữ riêng: không gợi ý gộp nữa, prompt dặn AI chỉ viết nghĩa của cấp mình. */
    @Transactional
    public void markDistinct(IdsRequest req) {
        List<Long> ids = req.ids() == null ? List.of() : req.ids().stream().distinct().sorted().toList();
        if (ids.size() < 2) {
            throw new IllegalArgumentException("Cần ít nhất hai mẫu");
        }
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                jdbc.update("INSERT INTO grammar_distinct (a, b) VALUES (:a, :b) ON CONFLICT DO NOTHING",
                        new MapSqlParameterSource("a", ids.get(i)).addValue("b", ids.get(j)));
            }
        }
    }

    private static List<Integer> levels(java.sql.Array array) throws java.sql.SQLException {
        if (array == null) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>();
        for (Object o : (Object[]) array.getArray()) {
            out.add(((Number) o).intValue());
        }
        out.sort(java.util.Comparator.reverseOrder());
        return out;
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
