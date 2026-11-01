package com.nihonmukae.backend.flashcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashcardStatsResponse {
    private long totalCards;
    private long learnedCards;
    private long dueToday;
    // Số ngày liên tiếp có ôn tập, tính tới hôm nay (hoặc hôm qua nếu hôm nay chưa ôn)
    private int streak;
}
