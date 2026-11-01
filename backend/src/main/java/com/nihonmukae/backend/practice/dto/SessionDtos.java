package com.nihonmukae.backend.practice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public final class SessionDtos {

    private SessionDtos() {
    }

    /** mode = practice: level, typeIds, questionCount, timeLimitMin, feedbackMode. mode = exam: level, testId hoặc random. */
    public record StartRequest(@NotBlank String mode, @NotNull Short level, List<Short> typeIds, Integer questionCount,
                               Integer timeLimitMin, String feedbackMode, Long testId, Boolean random) {
    }

    public record AnswerRequest(String selected) {
    }

    /** Trạng thái 1 khối: pending (chưa bắt đầu), active (đang làm), closed (đã nộp hoặc hết giờ). */
    public record BlockView(int blockNo, String name, Integer durationMin, int questionCount, String status,
                            ZonedDateTime startedAt, ZonedDateTime deadline) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GroupView(Long id, String title, String passage, String audioUrl, String imageUrl) {
    }

    /**
     * Câu hỏi gửi cho client. hidden = true với khối chưa bắt đầu hoặc đã đóng (exam): chỉ có số thứ tự và
     * đáp án đã chọn. Đáp án đúng và giải thích chỉ có khi đã khóa (feedback tức thì) hoặc sau khi nộp.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuestionView(int position, int blockNo, boolean hidden, Long questionId, String typeCode,
                               String typeName, String section, Long groupId, String question, List<String> options,
                               String audioUrl, String selected, boolean locked, Boolean correct,
                               String correctAnswer, String explanation) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SessionView(UUID id, String mode, int level, String title, String status, String feedbackMode,
                              ZonedDateTime serverNow, ZonedDateTime startedAt, Integer currentBlockNo,
                              List<BlockView> blocks, List<QuestionView> questions, List<GroupView> groups,
                              List<String> warnings) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AnswerResult(boolean saved, boolean locked, Boolean correct, String correctAnswer, String explanation) {
    }

    public record SessionSummary(UUID id, String mode, int level, String title, String status, Double score,
                                 Double maxScore, int correct, int total, ZonedDateTime startedAt,
                                 ZonedDateTime finishedAt) {
    }
}
