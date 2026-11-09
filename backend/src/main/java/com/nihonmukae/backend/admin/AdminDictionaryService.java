package com.nihonmukae.backend.admin;

import com.nihonmukae.backend.dictionary.DictionaryRepository;
import com.nihonmukae.backend.dictionary.dto.DictDtos.AdminContributionPage;
import com.nihonmukae.backend.dictionary.dto.DictDtos.AdminContributionRow;
import com.nihonmukae.backend.dictionary.dto.DictDtos.AdminEntryDetail;
import com.nihonmukae.backend.dictionary.dto.DictDtos.AdminEntryPage;
import com.nihonmukae.backend.dictionary.dto.DictDtos.AdminEntryRow;
import com.nihonmukae.backend.dictionary.dto.DictDtos.AdminSummary;
import com.nihonmukae.backend.dictionary.dto.DictDtos.Entry;
import com.nihonmukae.backend.dictionary.dto.DictDtos.EntryRequest;
import com.nihonmukae.backend.dictionary.dto.DictDtos.ExampleInput;
import com.nihonmukae.backend.dictionary.dto.DictDtos.ReviewRequest;
import com.nihonmukae.backend.shared.exception.NotFoundException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Quản lý dữ liệu từ điển: thêm, sửa, xoá mục từ và duyệt đóng góp của người dùng. */
@Service
public class AdminDictionaryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_MEANINGS = 30;
    private static final int MAX_EXAMPLES = 20;
    private static final Set<String> SOURCES = Set.of("ai", "tatoeba", "admin");

    private final NamedParameterJdbcTemplate jdbc;
    private final DictionaryRepository repository;

    public AdminDictionaryService(NamedParameterJdbcTemplate jdbc, DictionaryRepository repository) {
        this.jdbc = jdbc;
        this.repository = repository;
    }

    // ─── Mục từ ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminSummary summary() {
        return jdbc.getJdbcTemplate().queryForObject("""
                SELECT (SELECT count(*) FROM dict_entries),
                       (SELECT count(DISTINCT entry_id) FROM dict_meanings),
                       (SELECT count(*) FROM dict_entries WHERE edited),
                       (SELECT count(*) FROM dict_entries WHERE id >= ?),
                       (SELECT count(*) FROM dict_contributions WHERE status = 'PENDING')
                """, (rs, i) -> new AdminSummary(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4),
                rs.getLong(5)), DictionaryRepository.CUSTOM_ID_START);
    }

    /** filter: all | no_vi (chưa có nghĩa Việt) | edited | custom. */
    @Transactional(readOnly = true)
    public AdminEntryPage listEntries(String keyword, String filter, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        MapSqlParameterSource p = new MapSqlParameterSource("limit", safeSize).addValue("offset", safePage * safeSize)
                .addValue("custom", DictionaryRepository.CUSTOM_ID_START);
        List<String> where = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            String q = keyword.trim();
            p.addValue("like", "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
            String idMatch = "";
            if (q.matches("\\d{1,18}")) {
                p.addValue("id", Long.parseLong(q));
                idMatch = " OR e.id = :id";
            }
            where.add("(e.word ILIKE :like OR e.reading ILIKE :like OR e.headword ILIKE :like OR e.han_viet ILIKE :like"
                    + " OR EXISTS (SELECT 1 FROM dict_meanings m WHERE m.entry_id = e.id AND m.meaning ILIKE :like)"
                    + idMatch + ")");
        }
        if ("no_vi".equals(filter)) {
            where.add("NOT EXISTS (SELECT 1 FROM dict_meanings m WHERE m.entry_id = e.id)");
        } else if ("edited".equals(filter)) {
            where.add("e.edited");
        } else if ("custom".equals(filter)) {
            where.add("e.id >= :custom");
        }
        String clause = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);

        Long total = jdbc.queryForObject("SELECT count(*) FROM dict_entries e" + clause, p, Long.class);
        List<AdminEntryRow> rows = jdbc.query("""
                SELECT e.id, e.headword, e.reading, e.pos, e.jlpt_level, e.priority_group, e.edited,
                       EXISTS (SELECT 1 FROM dict_meanings m WHERE m.entry_id = e.id) AS has_vi,
                       COALESCE((SELECT string_agg(m.meaning, '; ' ORDER BY m.idx) FROM dict_meanings m
                                 WHERE m.entry_id = e.id), e.gloss_en -> 0 ->> 0) AS preview
                FROM dict_entries e""" + clause + """

                ORDER BY COALESCE(e.freq_rank, 999999), e.id
                LIMIT :limit OFFSET :offset
                """, p, (rs, i) -> new AdminEntryRow(rs.getLong("id"), rs.getString("headword"),
                rs.getString("reading"), rs.getString("pos"), DictionaryRepository.shortOrNull(rs.getObject("jlpt_level")),
                rs.getString("priority_group"), rs.getBoolean("has_vi"), rs.getString("preview"),
                rs.getBoolean("edited"), rs.getLong("id") >= DictionaryRepository.CUSTOM_ID_START));
        long count = total == null ? 0 : total;
        return new AdminEntryPage(rows, safePage, safeSize, count, (int) Math.ceil(count / (double) safeSize));
    }

    @Transactional(readOnly = true)
    public AdminEntryDetail getEntry(long id) {
        List<Entry> loaded = repository.load(List.of(id));
        if (loaded.isEmpty()) {
            throw new NotFoundException("Không tìm thấy mục từ");
        }
        Entry entry = loaded.get(0);
        MapSqlParameterSource p = new MapSqlParameterSource("id", id);
        return jdbc.query("SELECT usually_kana, edited, priority_group FROM dict_entries WHERE id = :id", p, rs -> {
            rs.next();
            return new AdminEntryDetail(entry, rs.getBoolean("usually_kana"), repository.glossEn(id),
                    rs.getBoolean("edited"), rs.getString("priority_group"));
        });
    }

    @Transactional
    public AdminEntryDetail create(EntryRequest req) {
        Validated v = validate(req, true);
        Long id = jdbc.getJdbcTemplate().queryForObject("SELECT nextval('dict_custom_id_seq')", Long.class);
        MapSqlParameterSource p = entryParams(id, v);
        jdbc.update("""
                INSERT INTO dict_entries (id, word, reading, headword, other_forms, other_readings, usually_kana, pos,
                                          jlpt_level, priority_group, han_viet, edited)
                VALUES (:id, :word, :reading, :headword, COALESCE(string_to_array(CAST(:forms AS text), :sep), '{}'),
                        COALESCE(string_to_array(CAST(:readings AS text), :sep), '{}'), :usuallyKana, :pos, :jlpt, 'custom', :hanViet, TRUE)
                """, p);
        replaceChildren(id, v);
        return getEntry(id);
    }

    /** Sửa một mục: thay toàn bộ nghĩa và ví dụ bằng bản gửi lên, và đánh dấu edited để import không ghi đè. */
    @Transactional
    public AdminEntryDetail update(long id, EntryRequest req) {
        Integer exists = jdbc.queryForObject("SELECT count(*) FROM dict_entries WHERE id = :id",
                new MapSqlParameterSource("id", id), Integer.class);
        if (exists == null || exists == 0) {
            throw new NotFoundException("Không tìm thấy mục từ");
        }
        Validated v = validate(req, false);
        jdbc.update("""
                UPDATE dict_entries SET word = :word, reading = :reading, headword = :headword,
                    other_forms = COALESCE(string_to_array(CAST(:forms AS text), :sep), '{}'), other_readings = COALESCE(string_to_array(CAST(:readings AS text), :sep), '{}'),
                    usually_kana = :usuallyKana, pos = :pos, jlpt_level = :jlpt, han_viet = :hanViet,
                    edited = TRUE, updated_at = now()
                WHERE id = :id
                """, entryParams(id, v));
        replaceChildren(id, v);
        return getEntry(id);
    }

    /** Xoá hẳn. Mục JMdict được ghi vào dict_deleted để import_dict.py không nạp lại. */
    @Transactional
    public void delete(long id) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", id);
        if (id < DictionaryRepository.CUSTOM_ID_START) {
            jdbc.update("INSERT INTO dict_deleted (id) VALUES (:id) ON CONFLICT DO NOTHING", p);
        }
        int deleted = jdbc.update("DELETE FROM dict_entries WHERE id = :id", p);
        if (deleted == 0) {
            throw new NotFoundException("Không tìm thấy mục từ");
        }
    }

    // ─── Đóng góp ───────────────────────────────────────────────────────

    /** status: PENDING | APPROVED | REJECTED, bỏ trống = tất cả. */
    @Transactional(readOnly = true)
    public AdminContributionPage listContributions(String status, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        MapSqlParameterSource p = new MapSqlParameterSource("limit", safeSize).addValue("offset", safePage * safeSize);
        String clause = "";
        if (status != null && Set.of("PENDING", "APPROVED", "REJECTED").contains(status)) {
            clause = " WHERE c.status = :status";
            p.addValue("status", status);
        }
        Long total = jdbc.queryForObject("SELECT count(*) FROM dict_contributions c" + clause, p, Long.class);
        List<AdminContributionRow> rows = jdbc.query("""
                SELECT c.id, c.entry_id, e.headword, e.reading, c.kind, c.ja, c.vi, c.status, c.review_note,
                       u.name, u.email, c.created_at, c.reviewed_at
                FROM dict_contributions c
                JOIN dict_entries e ON e.id = c.entry_id
                JOIN users u ON u.id = c.user_id""" + clause + """

                ORDER BY CASE c.status WHEN 'PENDING' THEN 0 ELSE 1 END, c.created_at DESC
                LIMIT :limit OFFSET :offset
                """, p, (rs, i) -> {
            OffsetDateTime reviewed = rs.getObject("reviewed_at", OffsetDateTime.class);
            return new AdminContributionRow(rs.getLong("id"), rs.getLong("entry_id"), rs.getString("headword"),
                    rs.getString("reading"), rs.getString("kind"), rs.getString("ja"), rs.getString("vi"),
                    rs.getString("status"), rs.getString("review_note"),
                    DictionaryRepository.displayName(rs.getString("name"), rs.getString("email")),
                    rs.getString("email"),
                    rs.getObject("created_at", OffsetDateTime.class).atZoneSameInstant(ZoneOffset.UTC),
                    reviewed == null ? null : reviewed.atZoneSameInstant(ZoneOffset.UTC));
        });
        long count = total == null ? 0 : total;
        return new AdminContributionPage(rows, safePage, safeSize, count, (int) Math.ceil(count / (double) safeSize));
    }

    @Transactional
    public void review(UUID adminId, long id, ReviewRequest req) {
        if (req == null || req.status() == null || !Set.of("APPROVED", "REJECTED", "PENDING").contains(req.status())) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ");
        }
        String note = req.note() == null || req.note().isBlank() ? null : req.note().trim();
        if (note != null && note.length() > 500) {
            throw new IllegalArgumentException("Ghi chú quá dài (tối đa 500 ký tự)");
        }
        int updated = jdbc.update("""
                UPDATE dict_contributions SET status = :status, review_note = :note,
                    reviewed_by = :admin, reviewed_at = now()
                WHERE id = :id
                """, new MapSqlParameterSource("status", req.status()).addValue("note", note)
                .addValue("admin", adminId).addValue("id", id));
        if (updated == 0) {
            throw new NotFoundException("Không tìm thấy đóng góp");
        }
    }

    @Transactional
    public void deleteContribution(long id) {
        int deleted = jdbc.update("DELETE FROM dict_contributions WHERE id = :id", new MapSqlParameterSource("id", id));
        if (deleted == 0) {
            throw new NotFoundException("Không tìm thấy đóng góp");
        }
    }

    // ─── Kiểm tra dữ liệu và ghi con ────────────────────────────────────

    private record Validated(String word, String reading, List<String> otherForms, List<String> otherReadings,
                             boolean usuallyKana, String pos, Short jlpt, String hanViet, List<String> meanings,
                             List<ExampleInput> examples) {
        String headword() {
            return word == null || usuallyKana ? reading : word;
        }
    }

    private Validated validate(EntryRequest req, boolean creating) {
        if (req == null) {
            throw new IllegalArgumentException("Thiếu dữ liệu");
        }
        String reading = clean(req.reading());
        if (reading == null || reading.length() > 200) {
            throw new IllegalArgumentException("Cách đọc (kana) là bắt buộc, tối đa 200 ký tự");
        }
        String word = clean(req.word());
        if (word != null && word.length() > 200) {
            throw new IllegalArgumentException("Mặt chữ kanji tối đa 200 ký tự");
        }
        if (req.jlptLevel() != null && (req.jlptLevel() < 1 || req.jlptLevel() > 5)) {
            throw new IllegalArgumentException("Cấp JLPT phải từ 1 đến 5");
        }
        List<String> meanings = new ArrayList<>();
        for (String m : req.meanings() == null ? List.<String>of() : req.meanings()) {
            String c = clean(m);
            if (c != null) {
                if (c.length() > 500) {
                    throw new IllegalArgumentException("Một nghĩa tối đa 500 ký tự");
                }
                meanings.add(c);
            }
        }
        if (meanings.size() > MAX_MEANINGS) {
            throw new IllegalArgumentException("Tối đa " + MAX_MEANINGS + " nghĩa");
        }
        if (creating && meanings.isEmpty()) {
            throw new IllegalArgumentException("Cần ít nhất một nghĩa tiếng Việt");
        }
        List<ExampleInput> examples = new ArrayList<>();
        for (ExampleInput x : req.examples() == null ? List.<ExampleInput>of() : req.examples()) {
            String ja = x == null ? null : clean(x.ja());
            String vi = x == null ? null : clean(x.vi());
            if (ja == null && vi == null) {
                continue;
            }
            if (ja == null || vi == null) {
                throw new IllegalArgumentException("Mỗi câu ví dụ cần cả câu tiếng Nhật và bản dịch");
            }
            if (ja.length() > 300 || vi.length() > 500) {
                throw new IllegalArgumentException("Câu ví dụ quá dài");
            }
            String source = x.source() != null && SOURCES.contains(x.source()) ? x.source() : "admin";
            examples.add(new ExampleInput(ja, vi, source, "tatoeba".equals(source) ? x.sourceId() : null));
        }
        if (examples.size() > MAX_EXAMPLES) {
            throw new IllegalArgumentException("Tối đa " + MAX_EXAMPLES + " câu ví dụ");
        }
        return new Validated(word, reading, cleanList(req.otherForms()), cleanList(req.otherReadings()),
                Boolean.TRUE.equals(req.usuallyKana()), clean(req.pos()), req.jlptLevel(), clean(req.hanViet()),
                meanings, examples);
    }

    private MapSqlParameterSource entryParams(long id, Validated v) {
        // Mảng gửi dạng chuỗi nối bằng ký tự phân cách để khỏi phụ thuộc kiểu mảng của driver
        return new MapSqlParameterSource("id", id).addValue("word", v.word()).addValue("reading", v.reading())
                .addValue("headword", v.headword()).addValue("forms", join(v.otherForms()))
                .addValue("readings", join(v.otherReadings())).addValue("sep", SEP).addValue("usuallyKana", v.usuallyKana())
                .addValue("pos", v.pos()).addValue("jlpt", v.jlpt()).addValue("hanViet", v.hanViet());
    }

    private void replaceChildren(long id, Validated v) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", id);
        jdbc.update("DELETE FROM dict_meanings WHERE entry_id = :id", p);
        jdbc.update("DELETE FROM dict_examples WHERE entry_id = :id", p);
        for (int i = 0; i < v.meanings().size(); i++) {
            jdbc.update("INSERT INTO dict_meanings (entry_id, idx, meaning) VALUES (:id, :idx, :text)",
                    new MapSqlParameterSource("id", id).addValue("idx", i).addValue("text", v.meanings().get(i)));
        }
        for (int i = 0; i < v.examples().size(); i++) {
            ExampleInput x = v.examples().get(i);
            jdbc.update("""
                    INSERT INTO dict_examples (entry_id, idx, ja, vi, source, source_id)
                    VALUES (:id, :idx, :ja, :vi, :source, :sourceId)
                    """, new MapSqlParameterSource("id", id).addValue("idx", i).addValue("ja", x.ja())
                    .addValue("vi", x.vi()).addValue("source", x.source()).addValue("sourceId", x.sourceId()));
        }
    }

    private static final String SEP = "\u001f";

    private static String join(List<String> values) {
        return values.isEmpty() ? null : String.join(SEP, values);
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static List<String> cleanList(List<String> values) {
        List<String> out = new ArrayList<>();
        for (String v : values == null ? List.<String>of() : values) {
            String c = clean(v);
            if (c != null && c.length() <= 200 && !out.contains(c)) {
                out.add(c);
            }
        }
        return out;
    }
}
