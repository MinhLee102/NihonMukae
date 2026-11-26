package com.nihonmukae.backend.flashcard;

import com.nihonmukae.backend.shared.entity.FlashcardReview;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudyPriorityTest {

    private static FlashcardReview review(String lastRating, int repetitions, float ease) {
        return FlashcardReview.builder().lastRating(lastRating).repetitions(repetitions).easeFactor(ease).build();
    }

    @Test
    void hardComesBeforeNormalThenNewThenEasy() {
        assertEquals(0, FlashcardService.priority(review("hard", 0, 1.96f)));
        assertEquals(1, FlashcardService.priority(review("normal", 2, 2.36f)));
        assertEquals(2, FlashcardService.priority(null));
        assertEquals(3, FlashcardService.priority(review("easy", 3, 2.6f)));
    }

    @Test
    void olderReviewsWithoutRatingAreGuessedFromSm2() {
        // Khó đặt lại chuỗi nhớ về 0
        assertEquals(0, FlashcardService.priority(review(null, 0, 2.18f)));
        // Bình thường làm hệ số dễ giảm dưới 2.5
        assertEquals(1, FlashcardService.priority(review(null, 2, 2.36f)));
        // Dễ giữ hoặc tăng hệ số dễ
        assertEquals(3, FlashcardService.priority(review(null, 2, 2.6f)));
    }

    @Test
    void cardIsDueWhenNeverReviewedOrPastItsDate() {
        ZonedDateTime now = ZonedDateTime.now();
        assertTrue(FlashcardService.isDue(null, now));
        assertTrue(FlashcardService.isDue(FlashcardReview.builder().nextReviewAt(now.minusHours(1)).build(), now));
        assertTrue(FlashcardService.isDue(FlashcardReview.builder().nextReviewAt(now).build(), now));
        assertFalse(FlashcardService.isDue(FlashcardReview.builder().nextReviewAt(now.plusDays(3)).build(), now));
    }
}
