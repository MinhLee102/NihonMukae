package com.nihonmukae.backend.flashcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResultResponse {
    private int intervalDays;
    private int repetitions;
    private float easeFactor;
    private ZonedDateTime nextReviewAt;
}
