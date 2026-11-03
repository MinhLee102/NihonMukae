package com.nihonmukae.backend.feedback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihonmukae.backend.feedback.FeedbackDtos.CreateRequest;
import com.nihonmukae.backend.feedback.FeedbackDtos.Page;
import com.nihonmukae.backend.feedback.FeedbackDtos.Row;
import com.nihonmukae.backend.feedback.FeedbackDtos.Summary;
import com.nihonmukae.backend.feedback.FeedbackDtos.UpdateRequest;
import com.nihonmukae.backend.shared.config.RateLimiter;
import com.nihonmukae.backend.shared.exception.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FeedbackService {

    static final Set<String> CATEGORIES = Set.of("exam_error", "bug", "suggestion", "transcript", "other");
    static final Set<String> STATUSES = Set.of("new", "seen", "fixed", "rejected");
    private static final List<String> CONTEXT_KEYS = List.of("questionId", "sessionId", "testId");
    private static final int MAX_MESSAGE = 4000;
    private static final int MAX_PAGE_SIZE = 100;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    // 10 góp ý mỗi giờ mỗi người, tránh spam
    private final RateLimiter limiter = new RateLimiter(10, 60 * 60_000L);

    public FeedbackService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public long create(UUID userId, CreateRequest req) {
        if (req.category() == null || !CATEGORIES.contains(req.category())) {
            throw new IllegalArgumentException("Loại góp ý không hợp lệ");
        }
        String message = req.message() == null ? "" : req.message().trim();
        if (message.length() < 5) {
            throw new IllegalArgumentException("Nội dung góp ý quá ngắn");
        }
        if (message.length() > MAX_MESSAGE) {
            throw new IllegalArgumentException("Nội dung góp ý tối đa " + MAX_MESSAGE + " ký tự");
        }
        if (!limiter.tryAcquire(userId.toString(), System.currentTimeMillis())) {
            throw new IllegalArgumentException("Bạn gửi góp ý quá nhiều, vui lòng thử lại sau");
        }
        String pageUrl = req.pageUrl() == null ? null : req.pageUrl().substring(0, Math.min(req.pageUrl().length(), 300));
        Long id = jdbc.queryForObject(
                "INSERT INTO feedback (user_id, category, message, page_url, context) VALUES (?, ?, ?, ?, ?::jsonb) RETURNING id",
                Long.class, userId, req.category(), message, pageUrl, contextJson(req.context()));
        return id == null ? 0 : id;
    }

    /** Giữ lại đúng 3 khóa, giá trị là số hoặc chuỗi ngắn. */
    private String contextJson(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        Map<String, Object> clean = new LinkedHashMap<>();
        for (String key : CONTEXT_KEYS) {
            Object v = raw.get(key);
            if (v instanceof Number || (v instanceof String s && s.length() <= 60)) {
                clean.put(key, v);
            }
        }
        try {
            return clean.isEmpty() ? null : mapper.writeValueAsString(clean);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public Page list(String status, String category, int page, int size) {
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int pageIndex = Math.max(page, 0);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (status != null && !status.isBlank()) {
            where.append(" AND f.status = ?");
            args.add(status);
        }
        if (category != null && !category.isBlank()) {
            where.append(" AND f.category = ?");
            args.add(category);
        }
        Long total = jdbc.queryForObject("SELECT count(*) FROM feedback f" + where, Long.class, args.toArray());
        args.add(pageSize);
        args.add(pageIndex * pageSize);
        List<Row> rows = jdbc.query("SELECT f.*, u.name AS user_name, u.email AS user_email "
                + "FROM feedback f LEFT JOIN users u ON u.id = f.user_id" + where
                + " ORDER BY (f.status = 'new') DESC, f.created_at DESC LIMIT ? OFFSET ?", this::toRow, args.toArray());
        long count = total == null ? 0 : total;
        return new Page(rows, pageIndex, pageSize, count, (int) Math.ceil(count / (double) pageSize));
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String s : List.of("new", "seen", "fixed", "rejected")) {
            byStatus.put(s, 0L);
        }
        jdbc.query("SELECT status, count(*) AS n FROM feedback GROUP BY status",
                rs -> {
                    byStatus.put(rs.getString("status"), rs.getLong("n"));
                });
        return new Summary(byStatus);
    }

    @Transactional
    public void update(long id, UpdateRequest req) {
        if (req.status() == null || !STATUSES.contains(req.status())) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ");
        }
        String note = req.adminNote() == null ? null : req.adminNote().substring(0, Math.min(req.adminNote().length(), 1000));
        int n = jdbc.update("UPDATE feedback SET status = ?, admin_note = ?, updated_at = now() WHERE id = ?",
                req.status(), note, id);
        if (n == 0) {
            throw new NotFoundException("Không tìm thấy góp ý");
        }
    }

    private Row toRow(ResultSet rs, int i) throws SQLException {
        JsonNode context = null;
        String raw = rs.getString("context");
        if (raw != null) {
            try {
                context = mapper.readTree(raw);
            } catch (JsonProcessingException ignored) {
                // context hỏng thì bỏ qua, không làm hỏng cả danh sách
            }
        }
        return new Row(rs.getLong("id"), rs.getObject("user_id", UUID.class), rs.getString("user_name"),
                rs.getString("user_email"), rs.getString("category"), rs.getString("message"), rs.getString("page_url"),
                context, rs.getString("status"), rs.getString("admin_note"),
                rs.getObject("created_at", OffsetDateTime.class).toZonedDateTime());
    }
}
