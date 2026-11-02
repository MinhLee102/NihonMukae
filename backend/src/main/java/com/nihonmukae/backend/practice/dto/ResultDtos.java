package com.nihonmukae.backend.practice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/** Kết quả đã chấm. Cả object này được lưu làm snapshot lúc nộp bài, lịch sử đọc lại từ đó. */
public final class ResultDtos {

    private ResultDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SectionScoreView(String code, double score, int max, int min, boolean belowMin) {
    }

    /** Điểm ước tính theo thang JLPT (chỉ với mode exam). passed = null nếu thiếu phần tính điểm. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JlptScore(double total, int maxTotal, Integer passScore, Boolean passed, boolean complete,
                            List<SectionScoreView> sections) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TypeStat(String typeCode, String typeName, String section, int correct, int total) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResultQuestion(int position, int blockNo, Long questionId, String typeCode, String typeName,
                                 String section, Long groupId, String question, List<String> options,
                                 String audioUrl, String selected, String correctAnswer, boolean correct,
                                 String explanation, double points, Integer mondaiNo, String instructionJa,
                                 String instructionVi) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResultGroup(Long id, String title, String passage, String audioUrl, String imageUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Result(UUID sessionId, String mode, int level, String title, ZonedDateTime startedAt,
                         ZonedDateTime finishedAt, int correct, int total, int unanswered, JlptScore jlpt,
                         List<TypeStat> byType, List<ResultQuestion> questions, List<ResultGroup> groups,
                         String listeningAudioUrl) {
    }
}
