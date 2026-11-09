package com.nihonmukae.backend.dictionary;

import com.nihonmukae.backend.dictionary.dto.DictDtos.ContributionRequest;
import com.nihonmukae.backend.dictionary.dto.DictDtos.MyContribution;
import com.nihonmukae.backend.dictionary.dto.DictDtos.PageData;
import com.nihonmukae.backend.shared.exception.NotFoundException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Đóng góp của người dùng (nghĩa bổ sung, câu ví dụ, ghi chú): lưu ở trạng thái PENDING, admin duyệt mới hiện. */
@Service
public class ContributionService {

    static final Set<String> KINDS = Set.of("meaning", "example", "note");
    private static final int MAX_PENDING_PER_USER = 30;
    private static final int MAX_MEANING = 300;
    private static final int MAX_EXAMPLE_JA = 300;
    private static final int MAX_EXAMPLE_VI = 500;
    private static final int MAX_NOTE = 1000;
    private static final int MAX_PAGE_SIZE = 50;
    private static final Pattern JAPANESE = Pattern.compile("[぀-ヿ㐀-鿿]");

    private final NamedParameterJdbcTemplate jdbc;

    public ContributionService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public MyContribution submit(UUID userId, long entryId, ContributionRequest req) {
        if (req == null || req.kind() == null || !KINDS.contains(req.kind())) {
            throw new IllegalArgumentException("Loại đóng góp không hợp lệ");
        }
        String kind = req.kind();
        String vi = req.vi() == null ? "" : req.vi().trim();
        String ja = req.ja() == null ? "" : req.ja().trim();
        if (vi.isEmpty()) {
            throw new IllegalArgumentException("Nội dung đóng góp không được để trống");
        }
        int maxVi = switch (kind) {
            case "meaning" -> MAX_MEANING;
            case "example" -> MAX_EXAMPLE_VI;
            default -> MAX_NOTE;
        };
        if (vi.length() > maxVi) {
            throw new IllegalArgumentException("Nội dung quá dài (tối đa " + maxVi + " ký tự)");
        }
        if ("example".equals(kind)) {
            if (ja.isEmpty() || !JAPANESE.matcher(ja).find()) {
                throw new IllegalArgumentException("Câu ví dụ cần có câu tiếng Nhật");
            }
            if (ja.length() > MAX_EXAMPLE_JA) {
                throw new IllegalArgumentException("Câu tiếng Nhật quá dài (tối đa " + MAX_EXAMPLE_JA + " ký tự)");
            }
        } else {
            ja = null;
        }

        MapSqlParameterSource p = new MapSqlParameterSource("entry", entryId).addValue("user", userId)
                .addValue("kind", kind).addValue("ja", ja).addValue("vi", vi);
        String headword = jdbc.query("SELECT headword FROM dict_entries WHERE id = :entry", p,
                rs -> rs.next() ? rs.getString(1) : null);
        if (headword == null) {
            throw new NotFoundException("Không tìm thấy từ trong từ điển");
        }
        Long pending = jdbc.queryForObject(
                "SELECT count(*) FROM dict_contributions WHERE user_id = :user AND status = 'PENDING'", p, Long.class);
        if (pending != null && pending >= MAX_PENDING_PER_USER) {
            throw new IllegalArgumentException("Bạn đang có quá nhiều đóng góp chờ duyệt, hãy đợi admin xử lý bớt");
        }
        Long duplicate = jdbc.queryForObject("""
                SELECT count(*) FROM dict_contributions
                WHERE user_id = :user AND entry_id = :entry AND kind = :kind AND lower(vi) = lower(:vi)
                  AND status IN ('PENDING', 'APPROVED')
                """, p, Long.class);
        if (duplicate != null && duplicate > 0) {
            throw new IllegalArgumentException("Bạn đã gửi đóng góp này rồi");
        }

        return jdbc.queryForObject("""
                INSERT INTO dict_contributions (entry_id, user_id, kind, ja, vi)
                VALUES (:entry, :user, :kind, :ja, :vi)
                RETURNING id, created_at
                """, p, (rs, i) -> new MyContribution(rs.getLong("id"), entryId, headword, kind, p.getValue("ja") == null
                ? null : String.valueOf(p.getValue("ja")), vi, "PENDING", null,
                rs.getObject("created_at", OffsetDateTime.class).atZoneSameInstant(ZoneOffset.UTC)));
    }

    @Transactional(readOnly = true)
    public PageData<MyContribution> mine(UUID userId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        MapSqlParameterSource p = new MapSqlParameterSource("user", userId)
                .addValue("limit", safeSize).addValue("offset", safePage * safeSize);
        Long total = jdbc.queryForObject("SELECT count(*) FROM dict_contributions WHERE user_id = :user", p, Long.class);
        List<MyContribution> rows = jdbc.query("""
                SELECT c.id, c.entry_id, e.headword, c.kind, c.ja, c.vi, c.status, c.review_note, c.created_at
                FROM dict_contributions c JOIN dict_entries e ON e.id = c.entry_id
                WHERE c.user_id = :user ORDER BY c.created_at DESC LIMIT :limit OFFSET :offset
                """, p, (rs, i) -> new MyContribution(rs.getLong("id"), rs.getLong("entry_id"),
                rs.getString("headword"), rs.getString("kind"), rs.getString("ja"), rs.getString("vi"),
                rs.getString("status"), rs.getString("review_note"),
                rs.getObject("created_at", OffsetDateTime.class).atZoneSameInstant(ZoneOffset.UTC)));
        long count = total == null ? 0 : total;
        return new PageData<>(rows, safePage, safeSize, count, (int) Math.ceil(count / (double) safeSize));
    }
}
