package com.nihonmukae.backend.flashcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeckResponse {
    private UUID id;
    private String name;
    private String description;
    private String color;
    private String icon;
    private long cardCount;
    // Số card đã học (đã review thành công ít nhất 1 lần)
    private long learnedCount;
    // Số card cần ôn ngay bây giờ (chưa học lần nào hoặc đã đến hạn)
    private long dueCount;
    private ZonedDateTime createdAt;
}
