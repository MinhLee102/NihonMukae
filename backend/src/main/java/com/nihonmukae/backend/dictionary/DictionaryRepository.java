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
            OR e.other_forms && ARRAY[:v]::text[] OR e.other_readings && ARRAY[:v]::text[]
            OR e.word LIKE ANY (ARRAY[:p]::text[]) OR e.reading LIKE ANY (ARRAY[:p]::text[])
            OR e.headword LIKE ANY (ARRAY[:p]::text[]))
            """;

    /** Khớp mặt chữ hoặc cách đọc: khớp đúng lên đầu, rồi các dạng khác, rồi khớp phần đầu. */
    public IdPage searchByForm(Set<String> variants, int offset, int limit) {
        List<String> prefixes = variants.stream().map(v -> escapeLike(v) + "%").toList();
        MapSqlParameterSource p = new MapSqlParameterSource("v", List.copyOf(variants))
                .addValue("p", prefixes).addValue("limit", limit).addValue("offset", offset);
        Long total = jdbc.queryForObject("SELECT count(*) FROM dict_entries e WHERE " + FORM_WHERE, p, Long.class);
        List<Long> ids = jdbc.queryForList("SELECT e.id FROM dict_entries e WHERE " + FORM_WHERE + """
                ORDER BY CASE WHEN e.word IN (:v) OR e.reading IN (:v) OR e.headword IN (:v) THEN 0
                              WHEN e.other_forms && ARRAY[:v]::text[] OR e.other_readings && ARRAY[:v]::text[] THEN 1
                              ELSE 2 END,
                         NOT EXISTS (SELECT 1 FROM dict_meanings m WHERE m.entry_id = e.id),
                         COALESCE(e.freq_rank, 999999), length(e.headword), e.id
                LIMIT :limit OFFSET :offset
                """, p, Long.class);
        return new IdPage(ids, total == null ? 0 : total);
    }

    private static final String MEANING_WHERE = """
            (EXISTS (SELECT 1 FROM dict_meanings m WHERE m.entry_id = e.id AND m.meaning ILIKE :like)
            OR e.han_viet ILIKE :like OR e.gloss_en::text ILIKE :like)
            """;

    /** Khớp theo nghĩa tiếng Việt, Hán Việt hoặc nghĩa tiếng Anh. */
    public IdPage searchByMeaning(String q, int offset, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource("like", "%" + escapeLike(q) + "%")
                .addValue("q", q).addValue("quoted", "%\"" + escapeLike(q) + "\"%")
                .addValue("limit", limit).addValue("offset", offset);
        Long total = jdbc.queryForObject("SELECT count(*) FROM dict_entries e WHERE " + MEANING_WHERE, p, Long.class);
        List<Long> ids = jdbc.queryForList("SELECT e.id FROM dict_entries e WHERE " + MEANING_WHERE + """
                ORDER BY CASE WHEN EXISTS (SELECT 1 FROM dict_meanings m WHERE m.entry_id = e.id AND lower(m.meaning) = lower(:q)) THEN 0
                              WHEN lower(e.han_viet) = lower(:q) OR e.gloss_en::text ILIKE :quoted THEN 1
                              ELSE 2 END,
                         COALESCE(e.freq_rank, 999999), length(e.headword), e.id
                LIMIT :limit OFFSET :offset
                """, p, Long.class);
        return new IdPage(ids, total == null ? 0 : total);
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
