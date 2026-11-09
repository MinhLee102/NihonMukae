package com.nihonmukae.backend.dictionary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihonmukae.backend.dictionary.dto.DictDtos.Entry;
import com.nihonmukae.backend.dictionary.dto.DictDtos.Example;
import com.nihonmukae.backend.dictionary.dto.DictDtos.Meaning;
import com.nihonmukae.backend.dictionary.dto.DictDtos.PublicContribution;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Truy vấn từ điển bằng JDBC: tìm id theo từng kiểu khớp, rồi nạp đầy đủ các mục theo danh sách id. */
@Repository
public class DictionaryRepository {

    /** Mục do admin tự thêm có id từ dãy này (dict_custom_id_seq), xa dãy ent_seq của JMdict. */
    public static final long CUSTOM_ID_START = 10_000_000_000L;

    private static final TypeReference<List<Object>> JSON_LIST = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public DictionaryRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public record IdPage(List<Long> ids, long total) {
    }

    // ─── Tìm id ─────────────────────────────────────────────────────────

    private static final String FORM_WHERE = """
            (e.word IN (:v) OR e.reading IN (:v) OR e.headword IN (:v)
            OR e.word IN (:b) OR e.reading IN (:b) OR e.headword IN (:b)
            OR e.other_forms && ARRAY[:v]::text[] OR e.other_readings && ARRAY[:v]::text[]
            OR e.word LIKE ANY (ARRAY[:p]::text[]) OR e.reading LIKE ANY (ARRAY[:p]::text[])
            OR e.headword LIKE ANY (ARRAY[:p]::text[]))
            """;

    // Giá trị không bao giờ khớp, để danh sách dạng gốc rỗng vẫn là mệnh đề IN hợp lệ
    private static final List<String> NO_MATCH = List.of("\uFFFF");

    /**
     * Khớp mặt chữ hoặc cách đọc: khớp đúng câu gõ vào lên đầu, rồi khớp dạng gốc của từ chia (食べました → 食べる),
     * rồi các dạng viết khác, rồi khớp phần đầu.
     */
    public IdPage searchByForm(Set<String> variants, List<String> bases, int offset, int limit) {
        List<String> prefixes = variants.stream().map(v -> escapeLike(v) + "%").toList();
        MapSqlParameterSource p = new MapSqlParameterSource("v", List.copyOf(variants))
                .addValue("b", bases.isEmpty() ? NO_MATCH : bases)
                .addValue("p", prefixes).addValue("limit", limit).addValue("offset", offset);
        Long total = jdbc.queryForObject("SELECT count(*) FROM dict_entries e WHERE " + FORM_WHERE, p, Long.class);
        List<Long> ids = jdbc.queryForList("SELECT e.id FROM dict_entries e WHERE " + FORM_WHERE + """
                ORDER BY CASE WHEN e.word IN (:v) OR e.reading IN (:v) OR e.headword IN (:v) THEN 0
                              WHEN e.word IN (:b) OR e.reading IN (:b) OR e.headword IN (:b) THEN 1
                              WHEN e.other_forms && ARRAY[:v]::text[] OR e.other_readings && ARRAY[:v]::text[] THEN 2
                              ELSE 3 END,
                         NOT EXISTS (SELECT 1 FROM dict_meanings m WHERE m.entry_id = e.id),
                         e.priority_group = 'other',
                         COALESCE(e.freq_rank, 999999), length(e.headword), e.id
                LIMIT :limit OFFSET :offset
                """, p, Long.class);
        return new IdPage(ids, total == null ? 0 : total);
    }

    /** Có mục nào mà mặt chữ hoặc cách đọc khớp đúng một trong các dạng này không (dùng để quyết định có hiểu câu gõ là romaji). */
    public boolean hasExactForm(Set<String> variants) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM (SELECT 1 FROM dict_entries e
                WHERE e.word IN (:v) OR e.reading IN (:v) OR e.headword IN (:v) LIMIT 1) t
                """, new MapSqlParameterSource("v", List.copyOf(variants)), Integer.class);
        return n != null && n > 0;
    }

    // Khớp nguyên từ: câu gõ vào phải đứng giữa khoảng trắng, dấu câu hoặc đầu/cuối chuỗi. Không dùng [[:alpha:]]
    // vì phụ thuộc locale của DB (chữ có dấu có thể không được coi là chữ cái). Tránh "ăn" khớp trong "năng", "chăn".
    private static final String SEP = "[] ,;.:()/\"'!?~=*+–-]";

    /**
     * Khớp theo nghĩa tiếng Việt, Hán Việt hoặc nghĩa tiếng Anh, nguyên từ. Một truy vấn lấy cả trang lẫn tổng số.
     * accentless: câu gõ không có dấu thì bỏ dấu cả hai phía (nhat ban → Nhật Bản); gõ có dấu thì khớp đúng dấu.
     * Ba nhánh UNION để mỗi nhánh dùng được chỉ mục trigram riêng (gộp bằng OR thì Postgres quét toàn bảng).
     */
    public IdPage searchByMeaning(String q, boolean accentless, int offset, int limit) {
        String re = "(^|" + SEP + ")" + regexQuote(q) + "($|" + SEP + ")";
        String meaning = accentless ? "immutable_unaccent(m.meaning)" : "m.meaning";
        String hanViet = accentless ? "immutable_unaccent(x.han_viet)" : "x.han_viet";
        String hanVietOrder = accentless ? "immutable_unaccent(lower(e.han_viet))" : "lower(e.han_viet)";
        String meaningOrder = accentless ? "immutable_unaccent(lower(m.meaning))" : "lower(m.meaning)";
        MapSqlParameterSource p = new MapSqlParameterSource("re", re)
                .addValue("q", q.toLowerCase()).addValue("quoted", "%\"" + escapeLike(q) + "\"%")
                .addValue("limit", limit).addValue("offset", offset);
        String sql = "SELECT e.id, count(*) OVER () AS total FROM dict_entries e WHERE e.id IN ("
                + "SELECT m.entry_id FROM dict_meanings m WHERE " + meaning + " ~* :re "
                + "UNION SELECT x.id FROM dict_entries x WHERE " + hanViet + " ~* :re "
                + "UNION SELECT y.id FROM dict_entries y WHERE y.gloss_text ~* :re) "
                + "ORDER BY CASE WHEN EXISTS (SELECT 1 FROM dict_meanings m WHERE m.entry_id = e.id AND "
                + meaningOrder + " = :q) THEN 0 "
                + "WHEN " + hanVietOrder + " = :q OR e.gloss_text ILIKE :quoted THEN 1 ELSE 2 END, "
                + "NOT EXISTS (SELECT 1 FROM dict_meanings m WHERE m.entry_id = e.id), "
                + "e.priority_group = 'other', "
                + "COALESCE(e.freq_rank, 999999), length(e.headword), e.id "
                + "LIMIT :limit OFFSET :offset";
        List<long[]> rows = jdbc.query(sql, p, (rs, i) -> new long[]{rs.getLong("id"), rs.getLong("total")});
        if (rows.isEmpty()) {
            return new IdPage(List.of(), 0);
        }
        return new IdPage(rows.stream().map(r -> r[0]).toList(), rows.get(0)[1]);
    }

    /** Gợi ý gần đúng bằng trigram khi gõ sai chính tả. */
    public List<Long> searchFuzzy(String q, int limit) {
        return jdbc.queryForList("""
                SELECT e.id FROM dict_entries e
                WHERE e.word % :q OR e.reading % :q
                ORDER BY GREATEST(similarity(coalesce(e.word, ''), :q), similarity(e.reading, :q)) DESC,
                         COALESCE(e.freq_rank, 999999)
                LIMIT :limit
                """, new MapSqlParameterSource("q", q).addValue("limit", limit), Long.class);
    }

    /** Mục tốt nhất cho một mặt chữ kanji; kana (có thể rỗng) dùng để chọn đúng cách đọc. */
    public Long findByKanji(Set<String> kanji, Set<String> kana) {
        MapSqlParameterSource p = new MapSqlParameterSource("k", List.copyOf(kanji));
        String readingRank = "1";
        if (!kana.isEmpty()) {
            p.addValue("r", List.copyOf(kana));
            readingRank = "CASE WHEN e.reading IN (:r) OR e.other_readings && ARRAY[:r]::text[] THEN 0 ELSE 1 END";
        }
        List<Long> ids = jdbc.queryForList("""
                SELECT e.id FROM dict_entries e
                WHERE e.word IN (:k) OR e.other_forms && ARRAY[:k]::text[]
                ORDER BY\s""" + readingRank + """
                , NOT EXISTS (SELECT 1 FROM dict_meanings m WHERE m.entry_id = e.id),
                  COALESCE(e.freq_rank, 999999), e.id
                LIMIT 1
                """, p, Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** Các từ có chứa chữ kanji này: từ đã có nghĩa Việt và từ phổ biến lên trước. */
    public List<Long> findIdsContaining(String kanji, int limit) {
        return jdbc.queryForList("""
                SELECT e.id FROM dict_entries e
                WHERE e.word LIKE :like
                ORDER BY NOT EXISTS (SELECT 1 FROM dict_meanings m WHERE m.entry_id = e.id),
                         e.priority_group = 'other', COALESCE(e.freq_rank, 999999), length(e.headword), e.id
                LIMIT :limit
                """, new MapSqlParameterSource("like", "%" + escapeLike(kanji) + "%").addValue("limit", limit),
                Long.class);
    }

    /** Từ thuần kana: ưu tiên mục chỉ viết bằng kana, rồi tới mục có kanji cùng cách đọc. */
    public Long findByKana(Set<String> kana) {
        List<Long> ids = jdbc.queryForList("""
                SELECT e.id FROM dict_entries e
                WHERE e.reading IN (:r) OR e.other_readings && ARRAY[:r]::text[]
                ORDER BY (e.word IS NULL OR e.usually_kana) DESC,
                         NOT EXISTS (SELECT 1 FROM dict_meanings m WHERE m.entry_id = e.id),
                         COALESCE(e.freq_rank, 999999), e.id
                LIMIT 1
                """, new MapSqlParameterSource("r", List.copyOf(kana)), Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    // ─── Nạp đầy đủ ─────────────────────────────────────────────────────

    /** Trả các mục theo đúng thứ tự ids (mục không tồn tại bị bỏ qua). */
    public List<Entry> load(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource p = new MapSqlParameterSource("ids", ids);
        Map<Long, EntryRow> rows = new HashMap<>();
        jdbc.query("""
                SELECT id, word, reading, headword, other_forms, other_readings, pos, jlpt_level, han_viet,
                       gloss_en::text AS gloss
                FROM dict_entries WHERE id IN (:ids)
                """, p, rs -> {
            rows.put(rs.getLong("id"), new EntryRow(rs.getLong("id"), rs.getString("word"), rs.getString("reading"),
                    rs.getString("headword"), strings(rs.getArray("other_forms")),
                    strings(rs.getArray("other_readings")), rs.getString("pos"), shortOrNull(rs.getObject("jlpt_level")),
                    rs.getString("han_viet"), rs.getString("gloss")));
        });

        Map<Long, List<Meaning>> meanings = new HashMap<>();
        jdbc.query("SELECT entry_id, meaning FROM dict_meanings WHERE entry_id IN (:ids) ORDER BY entry_id, idx", p,
                rs -> {
                    meanings.computeIfAbsent(rs.getLong("entry_id"), k -> new ArrayList<>())
                            .add(new Meaning("vi", null, rs.getString("meaning")));
                });

        Map<Long, List<Example>> examples = new HashMap<>();
        jdbc.query("""
                SELECT entry_id, ja, vi, source, source_id FROM dict_examples
                WHERE entry_id IN (:ids) ORDER BY entry_id, idx
                """, p, rs -> {
            examples.computeIfAbsent(rs.getLong("entry_id"), k -> new ArrayList<>())
                    .add(new Example(rs.getString("ja"), rs.getString("vi"), rs.getString("source"),
                            (Long) rs.getObject("source_id")));
        });

        Map<Long, List<PublicContribution>> contributions = new HashMap<>();
        jdbc.query("""
                SELECT c.id, c.entry_id, c.kind, c.ja, c.vi, c.created_at, u.name, u.email
                FROM dict_contributions c JOIN users u ON u.id = c.user_id
                WHERE c.entry_id IN (:ids) AND c.status = 'APPROVED' ORDER BY c.created_at
                """, p, rs -> {
            contributions.computeIfAbsent(rs.getLong("entry_id"), k -> new ArrayList<>())
                    .add(new PublicContribution(rs.getLong("id"), rs.getString("kind"), rs.getString("ja"),
                            rs.getString("vi"), displayName(rs.getString("name"), rs.getString("email")),
                            rs.getObject("created_at", OffsetDateTime.class).atZoneSameInstant(ZoneOffset.UTC)));
        });

        List<Entry> out = new ArrayList<>();
        for (Long id : ids) {
            EntryRow r = rows.get(id);
            if (r != null) {
                out.add(toEntry(r, meanings.getOrDefault(id, List.of()), examples.getOrDefault(id, List.of()),
                        contributions.getOrDefault(id, List.of())));
            }
        }
        return out;
    }

    /** Nghĩa tiếng Anh theo từng nghĩa của JMdict, mỗi phần tử là các gloss ghép bằng "; ". */
    public List<String> glossEn(Long id) {
        String json = jdbc.query("SELECT gloss_en::text FROM dict_entries WHERE id = :id",
                new MapSqlParameterSource("id", id), rs -> rs.next() ? rs.getString(1) : null);
        return parseGloss(json);
    }

    private Entry toEntry(EntryRow r, List<Meaning> vi, List<Example> examples,
                          List<PublicContribution> contributions) {
        List<Meaning> shown = vi;
        if (vi.isEmpty()) {
            shown = parseGloss(r.gloss).stream().map(g -> new Meaning("en", null, g)).toList();
        }
        return new Entry(r.id, r.word, r.reading, r.headword, r.otherForms, r.otherReadings, r.hanViet, r.pos,
                r.jlpt, !vi.isEmpty(), r.id >= CUSTOM_ID_START, shown, examples, contributions);
    }

    private List<String> parseGloss(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> out = new ArrayList<>();
            for (Object sense : mapper.readValue(json, JSON_LIST)) {
                if (sense instanceof List<?> glosses) {
                    out.add(String.join("; ", glosses.stream().map(String::valueOf).toList()));
                } else if (sense != null) {
                    out.add(String.valueOf(sense));
                }
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    private record EntryRow(long id, String word, String reading, String headword, List<String> otherForms,
                            List<String> otherReadings, String pos, Short jlpt, String hanViet, String gloss) {
    }

    // ─── Tiện ích ───────────────────────────────────────────────────────

    public static String displayName(String name, String email) {
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        return email == null ? "Ẩn danh" : email.split("@")[0];
    }

    private static List<String> strings(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        return Arrays.stream(values).map(String::valueOf).toList();
    }

    public static Short shortOrNull(Object o) {
        return o instanceof Number n ? Short.valueOf(n.shortValue()) : null;
    }

    static String regexQuote(String s) {
        return s.replaceAll("[\\\\.^$|?*+()\\[\\]{}]", "\\\\$0");
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Katakana ァ..ヶ lệch hiragana ぁ..ゖ đúng 0x60; ー và ký tự khác giữ nguyên. */
    static String toHiragana(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append(c >= 0x30A1 && c <= 0x30F6 ? (char) (c - 0x60) : c);
        }
        return sb.toString();
    }

    static String toKatakana(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append(c >= 0x3041 && c <= 0x3096 ? (char) (c + 0x60) : c);
        }
        return sb.toString();
    }

    /** Chính chuỗi gõ vào, cộng bản hiragana và katakana (nguồn dữ liệu không thống nhất cách lưu cách đọc). */
    static Set<String> kanaVariants(String s) {
        return new LinkedHashSet<>(List.of(s, toHiragana(s), toKatakana(s)));
    }
}
