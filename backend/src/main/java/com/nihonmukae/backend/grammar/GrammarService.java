package com.nihonmukae.backend.grammar;

import com.atilika.kuromoji.ipadic.Token;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihonmukae.backend.dictionary.Romaji;
import com.nihonmukae.backend.shared.exception.NotFoundException;
import com.nihonmukae.backend.shared.nlp.KuromojiHolder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ngữ pháp phía người học: tìm kiếm, danh sách theo cấp, chi tiết, và dò mẫu trong một câu. Chỉ mẫu đã có nội dung. */
@Service
public class GrammarService {

    static final String READY = "meaning IS NOT NULL AND NOT invalid";
    private static final int SEARCH_LIMIT = 50;
    public static final int MAX_DETECT_CHARS = 300;
    private static final long MATCHER_TTL_MS = 5 * 60 * 1000;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    private volatile GrammarMatcher matcher;
    private volatile Map<Long, Summary> matcherRows = Map.of();
    private volatile long matcherBuiltAt;

    public GrammarService(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public record Summary(long id, int level, List<Integer> extraLevels, String pattern, String reading, String meaning,
                          String category) {
    }

    public record Similar(String pattern, String diff) {
    }

    public record Example(String ja, String vi) {
    }

    public record Detail(long id, int level, List<Integer> extraLevels, String pattern, String reading, String meaning,
                         String category, List<String> connection, String explanation, String notes,
                         List<Similar> similar, List<Example> examples) {
    }

    /** Mẫu tìm thấy trong câu, ở đoạn [start, end). */
    public record Found(int start, int end, Summary point) {
    }

    public record DetectRequest(String text) {
    }

    private static final String SUMMARY_COLS = "id, level, extra_levels, pattern, reading, meaning, category";

    private static Summary summary(ResultSet rs) throws SQLException {
        return new Summary(rs.getLong("id"), rs.getInt("level"), AdminGrammarService.levels(rs.getArray("extra_levels")),
                rs.getString("pattern"), rs.getString("reading"), rs.getString("meaning"), rs.getString("category"));
    }

    /**
     * Tìm theo tên mẫu (bỏ 〜, khoảng trắng), cách đọc (gõ kana hoặc romaji) hoặc nghĩa tiếng Việt (có hoặc không dấu).
     * Khớp đúng tên lên đầu, rồi khớp phần đầu, rồi chứa, rồi khớp theo nghĩa; cùng hạng thì cấp dễ trước.
     */
    @Transactional(readOnly = true)
    public List<Summary> search(String q, Integer level) {
        String raw = q == null ? "" : q.trim();
        if (raw.isEmpty()) {
            return List.of();
        }
        if (level != null && (level < 1 || level > 5)) {
            throw new IllegalArgumentException("level phải từ 1 đến 5");
        }
        String key = GrammarDuplicates.patternKey(raw);
        if (key.isEmpty()) {
            return List.of();
        }
        String romaji = Romaji.toHiragana(raw);
        String kana = GrammarDuplicates.readingKey(romaji != null ? romaji : key);
        if (kana == null) {
            kana = key;
        }
        boolean accentless = Romaji.stripAccents(raw).equals(raw);
        String meaningCol = accentless ? "immutable_unaccent(lower(meaning))" : "lower(meaning)";
        String norm = "replace(replace(replace(replace(pattern, '〜', ''), '～', ''), ' ', ''), '…', '')";
        String readNorm = "replace(replace(coalesce(reading, ''), '〜', ''), ' ', '')";
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("key", key).addValue("kana", kana)
                .addValue("keyLike", like(key) + "%").addValue("kanaLike", like(kana) + "%")
                .addValue("keyAny", "%" + like(key) + "%").addValue("kanaAny", "%" + like(kana) + "%")
                .addValue("mean", "%" + like(raw.toLowerCase()) + "%")
                .addValue("level", level).addValue("limit", SEARCH_LIMIT);
        String levelFilter = level == null ? "" : " AND (level = :level OR :level = ANY(extra_levels))";
        String sql = "SELECT " + SUMMARY_COLS + ", CASE"
                + " WHEN " + norm + " = :key OR " + readNorm + " = :kana THEN 0"
                + " WHEN " + norm + " LIKE :keyLike OR " + readNorm + " LIKE :kanaLike THEN 1"
                + " WHEN " + norm + " LIKE :keyAny OR " + readNorm + " LIKE :kanaAny THEN 2"
                + " ELSE 3 END AS rank"
                + " FROM grammar_points WHERE " + READY + levelFilter
                + " AND (" + norm + " LIKE :keyAny OR " + readNorm + " LIKE :kanaAny OR " + meaningCol + " LIKE "
                + (accentless ? "immutable_unaccent(:mean)" : ":mean") + ")"
                + " ORDER BY rank, level DESC, sort_order, id LIMIT :limit";
        return jdbc.query(sql, p, (rs, i) -> summary(rs));
    }

    /** Các mẫu của một cấp (cấp chính hoặc cấp phụ), theo thứ tự trong danh sách. */
    @Transactional(readOnly = true)
    public List<Summary> byLevel(int level) {
        if (level < 1 || level > 5) {
            throw new IllegalArgumentException("level phải từ 1 đến 5");
        }
        return jdbc.query("SELECT " + SUMMARY_COLS + " FROM grammar_points WHERE " + READY
                        + " AND (level = :level OR :level = ANY(extra_levels))"
                        + " ORDER BY (level <> :level), sort_order, id",
                new MapSqlParameterSource("level", level), (rs, i) -> summary(rs));
    }

    @Transactional(readOnly = true)
    public Detail detail(long id) {
        List<Detail> rows = jdbc.query("SELECT " + SUMMARY_COLS + ", connection, explanation, notes, related"
                        + " FROM grammar_points WHERE id = :id AND " + READY,
                new MapSqlParameterSource("id", id), (rs, i) -> new Detail(rs.getLong("id"), rs.getInt("level"),
                        AdminGrammarService.levels(rs.getArray("extra_levels")), rs.getString("pattern"),
                        rs.getString("reading"), rs.getString("meaning"), rs.getString("category"),
                        readList(rs.getString("connection"), new TypeReference<List<String>>() { }),
                        rs.getString("explanation"), rs.getString("notes"),
                        readList(rs.getString("related"), new TypeReference<List<Similar>>() { }), List.of()));
        if (rows.isEmpty()) {
            throw new NotFoundException("Không tìm thấy mẫu ngữ pháp");
        }
        List<Example> examples = jdbc.query("SELECT ja, vi FROM grammar_examples WHERE point_id = :id ORDER BY idx",
                new MapSqlParameterSource("id", id), (rs, i) -> new Example(rs.getString("ja"), rs.getString("vi")));
        Detail d = rows.get(0);
        return new Detail(d.id(), d.level(), d.extraLevels(), d.pattern(), d.reading(), d.meaning(), d.category(),
                d.connection(), d.explanation(), d.notes(), d.similar(), examples);
    }

    /** Các mẫu xuất hiện trong text (một câu, tối đa MAX_DETECT_CHARS ký tự). */
    public List<Found> detect(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (text.length() > MAX_DETECT_CHARS) {
            throw new IllegalArgumentException("Đoạn văn quá dài (tối đa " + MAX_DETECT_CHARS + " ký tự)");
        }
        GrammarMatcher m = currentMatcher();
        Set<Integer> starts = new HashSet<>();
        for (Token t : KuromojiHolder.get().tokenize(text)) {
            starts.add(t.getPosition());
        }
        starts.add(text.length());
        Map<Long, Summary> rows = matcherRows;
        List<Found> out = new ArrayList<>();
        for (GrammarMatcher.Hit h : m.find(text, starts)) {
            Summary s = rows.get(h.id());
            if (s != null) {
                out.add(new Found(h.start(), h.end(), s));
            }
        }
        return out;
    }

    /** Bộ dò dựng từ mọi mẫu đã có nội dung, dựng lại sau vài phút để nhận mẫu mới. */
    private GrammarMatcher currentMatcher() {
        GrammarMatcher m = matcher;
        if (m != null && System.currentTimeMillis() - matcherBuiltAt < MATCHER_TTL_MS) {
            return m;
        }
        synchronized (this) {
            if (matcher != null && System.currentTimeMillis() - matcherBuiltAt < MATCHER_TTL_MS) {
                return matcher;
            }
            Map<Long, Summary> rows = new HashMap<>();
            List<GrammarMatcher.Point> points = new ArrayList<>();
            // Cấp dễ trước để khi hai mẫu khớp cùng đoạn thì mẫu cấp dễ được giữ
            jdbc.query("SELECT " + SUMMARY_COLS + " FROM grammar_points WHERE " + READY
                    + " ORDER BY level DESC, sort_order, id", rs -> {
                Summary s = summary(rs);
                rows.put(s.id(), s);
                points.add(new GrammarMatcher.Point(s.id(), s.pattern(), s.reading()));
            });
            matcher = new GrammarMatcher(points);
            matcherRows = rows;
            matcherBuiltAt = System.currentTimeMillis();
            return matcher;
        }
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<T> v = mapper.readValue(json, type);
            return v == null ? List.of() : v;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String like(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
