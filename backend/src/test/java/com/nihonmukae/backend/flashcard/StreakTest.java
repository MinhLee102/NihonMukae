package com.nihonmukae.backend.flashcard;

import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreakTest {

    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    private Date daysAgo(int n) {
        return Date.valueOf(today.minusDays(n));
    }

    @Test
    void noReviewsMeansZero() {
        assertEquals(0, FlashcardService.calculateStreak(List.of()));
    }

    @Test
    void consecutiveDaysIncludingToday() {
        assertEquals(3, FlashcardService.calculateStreak(List.of(daysAgo(0), daysAgo(1), daysAgo(2))));
    }

    @Test
    void streakSurvivesIfOnlyYesterdayReviewed() {
        assertEquals(2, FlashcardService.calculateStreak(List.of(daysAgo(1), daysAgo(2))));
    }

    @Test
    void gapBreaksStreak() {
        assertEquals(1, FlashcardService.calculateStreak(List.of(daysAgo(0), daysAgo(2))));
    }

    @Test
    void oldReviewsOnlyMeansZero() {
        assertEquals(0, FlashcardService.calculateStreak(List.of(daysAgo(3))));
    }
}
