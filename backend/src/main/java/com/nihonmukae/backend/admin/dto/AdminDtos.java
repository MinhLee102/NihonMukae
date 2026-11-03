package com.nihonmukae.backend.admin.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AdminDtos {

    private AdminDtos() {
    }

    // ─── Kho câu hỏi ────────────────────────────────────────────────────

    public record QuestionRow(Long id, int level, String typeCode, String typeName, String section, String question,
                              int optionCount, String correct, boolean hasAudio, Long groupId, Long testId,
                              String source, ZonedDateTime createdAt) {
    }

    public record QuestionPage(List<QuestionRow> content, int page, int size, long totalElements, int totalPages) {
    }

    public record TestRow(long id, int level, String title, Integer year, Integer month, boolean hasAudio,
                          long questionCount, long activeCount) {
    }

    /** Số câu đang hoạt động theo phần thi, và theo level, để hiện các thẻ thống kê. */
    public record BankSummary(Map<String, Long> bySection, Map<Integer, Long> byLevel, long total) {
    }

    // ─── Người dùng ─────────────────────────────────────────────────────

    public record UserRow(UUID id, String name, String email, String role, Short targetLevel, boolean enabled,
                          ZonedDateTime createdAt, ZonedDateTime lastLoginAt) {
    }

    public record UserPage(List<UserRow> content, int page, int size, long totalElements, int totalPages) {
    }

    /** active = chưa bị khóa và có đăng nhập trong 30 ngày qua. */
    public record UserSummary(long total, long active, long locked, long newLast30Days) {
    }

    public record EnabledRequest(boolean enabled) {
    }

    // ─── Trạng thái server ──────────────────────────────────────────────

    public record Memory(long totalBytes, long usedBytes, long freeBytes) {
    }

    public record Jvm(long heapUsedBytes, long heapMaxBytes, int threads, long uptimeMs, String javaVersion) {
    }

    public record Disk(long totalBytes, long usedBytes, long freeBytes) {
    }

    public record Database(boolean up, Long sizeBytes, Integer activeConnections, Integer maxConnections) {
    }

    public record Cache(boolean up, Long usedMemoryBytes, Long pingMs) {
    }

    public record Traffic(long requests, long serverErrors, long clientErrors) {
    }

    public record Counts(long users, long words, long activeQuestions, long sessions, long mediaFiles, long mediaBytes) {
    }

    /** Ảnh chụp trạng thái backend: RAM của máy, heap Java, CPU, ổ đĩa, DB, Redis, lưu lượng và số liệu dữ liệu. */
    public record ServerStatus(ZonedDateTime time, Memory memory, Jvm jvm, Double cpuLoadPercent,
                               Double loadAverage, int cpuCores, Disk disk, Database database, Cache cache,
                               Traffic traffic, Counts counts, String mediaProvider) {
    }
}
