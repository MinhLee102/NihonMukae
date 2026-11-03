package com.nihonmukae.backend.feedback;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FeedbackDtos {

    private FeedbackDtos() {
    }

    /** context chỉ nhận questionId, sessionId, testId; các khóa khác bị bỏ. */
    public record CreateRequest(String category, String message, String pageUrl, Map<String, Object> context) {
    }

    public record UpdateRequest(String status, String adminNote) {
    }

    public record Row(long id, UUID userId, String userName, String userEmail, String category, String message,
                      String pageUrl, JsonNode context, String status, String adminNote, ZonedDateTime createdAt) {
    }

    public record Page(List<Row> content, int page, int size, long totalElements, int totalPages) {
    }

    /** Số góp ý theo trạng thái, để hiện huy hiệu "mới". */
    public record Summary(Map<String, Long> byStatus) {
    }
}
