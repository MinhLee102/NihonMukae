package com.nihonmukae.backend.dictionary.dto;

import java.time.ZonedDateTime;
import java.util.List;

/** DTO của từ điển (bảng dict_*). */
public final class DictDtos {

    private DictDtos() {
    }

    /** lang = "vi" (nghĩa tiếng Việt) hoặc "en" (nghĩa JMdict, dùng khi từ chưa có bản dịch Việt). */
    public record Meaning(String lang, String pos, String meaning) {
    }

    /** source: "ai" | "tatoeba" | "admin". sourceId là mã câu Tatoeba (dẫn link ghi nguồn). */
    public record Example(String sentence, String translation, String source, Long sourceId) {
    }

    /** Đóng góp đã được admin duyệt, hiện công khai kèm tên người đóng góp. */
    public record PublicContribution(long id, String kind, String ja, String vi, String contributor,
                                     ZonedDateTime createdAt) {
    }

    /** Một mục từ điển đầy đủ. kanji = null với từ thuần kana. */
    public record Entry(Long id, String kanji, String kana, String headword, List<String> otherForms,
                        List<String> otherReadings, String hanViet, String pos, Short jlptLevel,
                        boolean hasVietnamese, boolean custom, List<Meaning> meanings, List<Example> examples,
                        List<PublicContribution> contributions) {
    }

    /** matchType: word_form | meaning | fuzzy | none. */
    public record SearchResult(PageData<Entry> results, String matchType) {
    }

    public record PageData<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }

    public record HistoryItem(Long id, Long wordId, String kanji, String kana, String meaning, String rawQuery,
                              ZonedDateTime searchedAt) {
    }

    // ─── Hán tự ─────────────────────────────────────────────────────────

    public record KanjiSummary(String kanji, List<String> hanViet, String meaningVi, List<String> meaningsEn,
                               List<String> onYomi, List<String> kunYomi, Integer strokeCount, Integer grade) {
    }

    /** strokePaths: thuộc tính d của từng nét theo thứ tự viết (KanjiVG, hệ toạ độ 109x109). */
    public record KanjiDetail(String kanji, List<String> hanViet, String meaningVi, List<String> meaningsEn,
                              List<String> onYomi, List<String> kunYomi, Integer strokeCount, Integer grade,
                              Integer frequency, Integer radical, String radicalChar, List<String> strokePaths,
                              List<Entry> words) {
    }

    // ─── Đóng góp của người dùng ────────────────────────────────────────

    /** kind = meaning | example | note. ja chỉ dùng cho example. */
    public record ContributionRequest(String kind, String ja, String vi) {
    }

    public record MyContribution(long id, long entryId, String headword, String kind, String ja, String vi,
                                 String status, String reviewNote, ZonedDateTime createdAt) {
    }

    // ─── Quản trị ───────────────────────────────────────────────────────

    public record AdminEntryRow(Long id, String headword, String reading, String pos, Short jlptLevel,
                                String priorityGroup, boolean hasVietnamese, String preview, boolean edited,
                                boolean custom) {
    }

    public record AdminEntryPage(List<AdminEntryRow> content, int page, int size, long totalElements,
                                 int totalPages) {
    }

    public record AdminSummary(long entries, long withVietnamese, long edited, long custom,
                               long pendingContributions) {
    }

    public record ExampleInput(String ja, String vi, String source, Long sourceId) {
    }

    /** Dùng cho cả thêm mới và sửa. meanings là nghĩa tiếng Việt, mỗi phần tử một nghĩa. */
    public record EntryRequest(String word, String reading, List<String> otherForms, List<String> otherReadings,
                               Boolean usuallyKana, String pos, Short jlptLevel, String hanViet,
                               List<String> meanings, List<ExampleInput> examples) {
    }

    /** Chi tiết để sửa: Entry cộng với nghĩa tiếng Anh của JMdict (chỉ xem) và cờ edited. */
    public record AdminEntryDetail(Entry entry, boolean usuallyKana, List<String> glossEn, boolean edited,
                                   String priorityGroup) {
    }

    public record AdminContributionRow(long id, long entryId, String headword, String reading, String kind,
                                       String ja, String vi, String status, String reviewNote, String userName,
                                       String userEmail, ZonedDateTime createdAt, ZonedDateTime reviewedAt) {
    }

    public record AdminContributionPage(List<AdminContributionRow> content, int page, int size, long totalElements,
                                        int totalPages) {
    }

    public record ReviewRequest(String status, String note) {
    }
}
