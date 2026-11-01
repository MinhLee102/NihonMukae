package com.nihonmukae.backend.question.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public final class ImportDtos {

    private ImportDtos() {
    }

    public record Problem(String path, String message) {
    }

    public record Duplicate(String path, String existing) {
    }

    /** Kết quả xem trước: chưa lưu gì. canCommit = false nếu còn lỗi. */
    public record PreviewResponse(UUID previewId, int questionCount, int groupCount, String testTitle,
                                  List<Problem> errors, List<Duplicate> duplicates, boolean canCommit) {
    }

    public record CommitRequest(@NotNull UUID previewId, String onDuplicate) {
    }

    public record CommitResponse(int createdQuestions, int createdGroups, int skipped, int overwritten, Long testId) {
    }
}
