package com.nihonmukae.backend.flashcard.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/** DTO của quiz bộ thẻ. */
public final class QuizDtos {

    private QuizDtos() {
    }

    /**
     * Một câu hỏi 4 đáp án. kind: meaning (từ, chọn nghĩa), reading (Hán tự, chọn cách đọc), cloze (điền từ vào câu).
     * prompt là từ được hỏi (meaning, reading) hoặc câu có chỗ trống （　　） (cloze). answer là chỉ số đáp án đúng.
     * word, reading, meaning, translation để hiện giải thích sau khi trả lời.
     */
    public record QuizQuestion(UUID cardId, String kind, String prompt, List<String> options, int answer,
                               String word, String reading, String meaning, String translation) {
    }

    public record AttemptRequest(UUID deckId, List<String> kinds, Integer total, Integer correct) {
    }

    public record Attempt(UUID id, UUID deckId, String deckName, List<String> kinds, int total, int correct,
                          ZonedDateTime createdAt) {
    }
}
