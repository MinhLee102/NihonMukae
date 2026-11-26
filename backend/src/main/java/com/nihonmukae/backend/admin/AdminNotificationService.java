package com.nihonmukae.backend.admin;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Những việc đang chờ admin xử lý: góp ý mới, đóng góp từ điển chờ duyệt, tài khoản chờ duyệt (chỉ ADMIN).
 * Trang quản trị hỏi định kỳ để hiện số trên menu và trong chuông thông báo.
 */
@Service
@Transactional(readOnly = true)
public class AdminNotificationService {

    private static final int LATEST = 5;

    private final NamedParameterJdbcTemplate jdbc;

    public AdminNotificationService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Item(String id, String title, String detail, ZonedDateTime createdAt) {
    }

    /** kind: feedback | contributions | users. href: trang xử lý, đã lọc sẵn mục đang chờ. */
    public record Group(String kind, String label, String href, long count, List<Item> latest) {
    }

    public record Notifications(long total, List<Group> groups) {
    }

    public Notifications get(boolean admin) {
        List<Group> groups = new ArrayList<>();
        groups.add(new Group("feedback", "Góp ý mới", "/admin/feedback",
                count("SELECT count(*) FROM feedback WHERE status = 'new'"),
                jdbc.query("""
                        SELECT id, category, message, created_at FROM feedback
                        WHERE status = 'new' ORDER BY created_at DESC LIMIT :n
                        """, new MapSqlParameterSource("n", LATEST),
                        (rs, i) -> new Item(rs.getString("id"), categoryLabel(rs.getString("category")),
                                shorten(rs.getString("message")), at(rs)))));
        groups.add(new Group("contributions", "Đóng góp từ điển chờ duyệt", "/admin/contributions",
                count("SELECT count(*) FROM dict_contributions WHERE status = 'PENDING'"),
                jdbc.query("""
                        SELECT c.id, e.headword, c.kind, c.vi, c.created_at FROM dict_contributions c
                        JOIN dict_entries e ON e.id = c.entry_id
                        WHERE c.status = 'PENDING' ORDER BY c.created_at DESC LIMIT :n
                        """, new MapSqlParameterSource("n", LATEST),
                        (rs, i) -> new Item(rs.getString("id"), rs.getString("headword") + " · " + kindLabel(rs.getString("kind")),
                                shorten(rs.getString("vi")), at(rs)))));
        if (admin) {
            groups.add(new Group("users", "Tài khoản chờ duyệt", "/admin/users?status=pending",
                    count("SELECT count(*) FROM users WHERE approved = false"),
                    jdbc.query("""
                            SELECT id, name, email, created_at FROM users
                            WHERE approved = false ORDER BY created_at DESC LIMIT :n
                            """, new MapSqlParameterSource("n", LATEST),
                            (rs, i) -> new Item(rs.getString("id"),
                                    rs.getString("name") == null || rs.getString("name").isBlank() ? rs.getString("email") : rs.getString("name"),
                                    rs.getString("email"), at(rs)))));
        }
        long total = groups.stream().mapToLong(Group::count).sum();
        return new Notifications(total, groups);
    }

    private long count(String sql) {
        Long n = jdbc.getJdbcTemplate().queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    private static ZonedDateTime at(ResultSet rs) throws SQLException {
        OffsetDateTime t = rs.getObject("created_at", OffsetDateTime.class);
        return t == null ? null : t.atZoneSameInstant(ZoneOffset.UTC);
    }

    static String shorten(String s) {
        if (s == null) {
            return "";
        }
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() <= 100 ? one : one.substring(0, 99) + "…";
    }

    private static String categoryLabel(String category) {
        return switch (category == null ? "" : category) {
            case "exam_error" -> "Đề thi sai";
            case "bug" -> "Lỗi hệ thống";
            case "suggestion" -> "Đề xuất";
            case "transcript" -> "Transcript câu nghe";
            default -> "Khác";
        };
    }

    private static String kindLabel(String kind) {
        return switch (kind == null ? "" : kind) {
            case "meaning" -> "nghĩa";
            case "example" -> "câu ví dụ";
            default -> "ghi chú";
        };
    }
}
