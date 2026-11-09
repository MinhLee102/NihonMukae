package com.nihonmukae.backend.dictionary;

import com.nihonmukae.backend.dictionary.dto.DictDtos.Entry;
import com.nihonmukae.backend.dictionary.dto.DictDtos.KanjiDetail;
import com.nihonmukae.backend.dictionary.dto.DictDtos.KanjiSummary;
import com.nihonmukae.backend.shared.exception.NotFoundException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tab Hán tự: tìm chữ kanji (theo chữ, Hán Việt, nghĩa, cách đọc) và xem chi tiết kèm thứ tự nét. */
@Service
@Transactional(readOnly = true)
public class KanjiService {

    private static final Pattern HAN = Pattern.compile("[㐀-䶿一-鿿豈-﫿]");
    private static final Pattern KANA = Pattern.compile("^[぀-ヿー]+$");
    private static final int MAX_RESULTS = 30;
    private static final int MAX_WORDS = 15;
    private static final String SEP = "[] ,;.:()/\"'!?~=*+–-]";

    private static final String COLUMNS = """
            kanji, han_viet, meaning, meanings_en, on_yomi, kun_yomi, stroke_count, grade, frequency, radical, stroke_paths
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final DictionaryRepository repository;

    public KanjiService(NamedParameterJdbcTemplate jdbc, DictionaryRepository repository) {
        this.jdbc = jdbc;
        this.repository = repository;
    }

    public List<KanjiSummary> search(String raw) {
        String q = raw == null ? "" : raw.trim();
        if (q.isEmpty() || q.length() > 100) {
            return List.of();
        }

        // Có chữ Hán trong câu gõ: trả đúng các chữ đó theo thứ tự xuất hiện
        Set<String> chars = new LinkedHashSet<>();
        Matcher m = HAN.matcher(q);
        while (m.find() && chars.size() < MAX_RESULTS) {
            chars.add(m.group());
        }
        if (!chars.isEmpty()) {
            List<KanjiSummary> found = jdbc.query("SELECT " + COLUMNS + " FROM dict_kanji WHERE kanji IN (:k)",
                    new MapSqlParameterSource("k", List.copyOf(chars)), (rs, i) -> summary(rs));
            List<String> order = List.copyOf(chars);
            return found.stream().sorted((a, b) -> Integer.compare(order.indexOf(a.kanji()), order.indexOf(b.kanji())))
                    .toList();
        }

        MapSqlParameterSource p = new MapSqlParameterSource("q", q.toLowerCase()).addValue("limit", MAX_RESULTS);
        List<String> conditions = new ArrayList<>();

        // Cách đọc: kana hoặc romaji (on là katakana, kun là hiragana, bỏ dấu . và - của okurigana)
        String kana = KANA.matcher(q).matches() ? q : Romaji.toHiragana(q);
        if (kana != null) {
            p.addValue("hira", DictionaryRepository.toHiragana(kana)).addValue("kata", DictionaryRepository.toKatakana(kana));
            conditions.add("""
                    EXISTS (SELECT 1 FROM unnest(on_yomi) o WHERE replace(o, '-', '') = :kata)
                    OR EXISTS (SELECT 1 FROM unnest(kun_yomi) k WHERE replace(replace(k, '.', ''), '-', '') = :hira
                                                                  OR split_part(replace(k, '-', ''), '.', 1) = :hira)""");
        }

        // Hán Việt (không dấu cũng được) và nghĩa tiếng Việt, tiếng Anh
        boolean accentless = Romaji.stripAccents(q).equals(q);
        String hv = accentless ? "immutable_unaccent(h)" : "h";
        String re = "(^|" + SEP + ")" + DictionaryRepository.regexQuote(q) + "($|" + SEP + ")";
        p.addValue("re", re);
        String hanVietExact = "EXISTS (SELECT 1 FROM unnest(han_viet) h WHERE " + hv + " = :q)";
        conditions.add(hanVietExact);
        conditions.add("meaning ~* :re");
        conditions.add("EXISTS (SELECT 1 FROM unnest(meanings_en) e WHERE lower(e) = :q)");

        // Chữ thường dùng lên trước, nên 車 (xe) đứng trước các chữ hiếm có Hán Việt gần giống
        String order = "";
        return jdbc.query("SELECT " + COLUMNS + " FROM dict_kanji WHERE (" + String.join(") OR (", conditions) + ") ORDER BY "
                + order + "frequency NULLS LAST, stroke_count NULLS LAST, kanji LIMIT :limit", p, (rs, i) -> summary(rs));
    }

    public KanjiDetail detail(String kanji) {
        if (kanji == null || kanji.codePointCount(0, kanji.length()) != 1) {
            throw new IllegalArgumentException("Cần đúng một chữ kanji");
        }
        List<KanjiDetail> rows = jdbc.query("SELECT " + COLUMNS + " FROM dict_kanji WHERE kanji = :k",
                new MapSqlParameterSource("k", kanji), (rs, i) -> {
                    Integer radical = (Integer) rs.getObject("radical");
                    return new KanjiDetail(rs.getString("kanji"), strings(rs.getArray("han_viet")), rs.getString("meaning"),
                            strings(rs.getArray("meanings_en")), strings(rs.getArray("on_yomi")),
                            strings(rs.getArray("kun_yomi")), (Integer) rs.getObject("stroke_count"),
                            (Integer) rs.getObject("grade"), (Integer) rs.getObject("frequency"), radical,
                            radical == null ? null : new String(Character.toChars(0x2F00 + radical - 1)),
                            strings(rs.getArray("stroke_paths")), List.of());
                });
        if (rows.isEmpty()) {
            throw new NotFoundException("Không tìm thấy chữ kanji này");
        }
        KanjiDetail d = rows.get(0);
        List<Entry> words = repository.load(repository.findIdsContaining(kanji, MAX_WORDS));
        return new KanjiDetail(d.kanji(), d.hanViet(), d.meaningVi(), d.meaningsEn(), d.onYomi(), d.kunYomi(),
                d.strokeCount(), d.grade(), d.frequency(), d.radical(), d.radicalChar(), d.strokePaths(), words);
    }

    private static KanjiSummary summary(ResultSet rs) throws SQLException {
        return new KanjiSummary(rs.getString("kanji"), strings(rs.getArray("han_viet")), rs.getString("meaning"),
                strings(rs.getArray("meanings_en")), strings(rs.getArray("on_yomi")), strings(rs.getArray("kun_yomi")),
                (Integer) rs.getObject("stroke_count"), (Integer) rs.getObject("grade"));
    }

    private static List<String> strings(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        return Arrays.stream((Object[]) array.getArray()).map(String::valueOf).toList();
    }
}
