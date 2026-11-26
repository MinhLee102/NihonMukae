package com.nihonmukae.backend.flashcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardResponse {
    private UUID id;
    private UUID deckId;
    private String front;
    private String back;
    private String imageUrl;
    private Long wordId;
    private ZonedDateTime createdAt;
    // Chỉ điền khi lấy danh sách card của deck. null nghĩa là chưa học lần nào
    private ZonedDateTime nextReviewAt;
    // Chỉ điền khi lấy thẻ để ôn, với thẻ tạo từ từ điển: Hán Việt và một câu ví dụ có bản dịch, lấy từ từ điển
    private String hanViet;
    private String exampleJa;
    private String exampleVi;
}
