package com.nihonmukae.backend.flashcard;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SrsServiceTest {

    private final SrsService srs = new SrsService();
    private final ZonedDateTime now = ZonedDateTime.parse("2026-01-01T00:00:00Z");

    @Test
    void firstSuccessfulReviewGivesOneDay() {
        var s = srs.calculate(2.5f, 1, 0, SrsService.GRADE_NORMAL, now);
        assertEquals(1, s.intervalDays());
        assertEquals(1, s.repetitions());
        assertEquals(now.plusDays(1), s.nextReviewAt());
    }

    @Test
    void secondSuccessfulReviewGivesSixDays() {
        var s = srs.calculate(2.5f, 1, 1, SrsService.GRADE_NORMAL, now);
        assertEquals(6, s.intervalDays());
        assertEquals(2, s.repetitions());
    }

    @Test
    void laterReviewMultipliesByEaseFactor() {
        var s = srs.calculate(2.5f, 6, 2, SrsService.GRADE_EASY, now);
        assertEquals(15, s.intervalDays());
        assertEquals(3, s.repetitions());
    }

    @Test
    void failingResetsRepetitionsAndInterval() {
        var s = srs.calculate(2.5f, 15, 5, SrsService.GRADE_HARD, now);
        assertEquals(0, s.repetitions());
        assertEquals(1, s.intervalDays());
    }

    @Test
    void easeFactorNeverDropsBelowMinimum() {
        float ef = 1.4f;
        for (int i = 0; i < 10; i++) {
            ef = srs.calculate(ef, 1, 0, 0, now).easeFactor();
        }
        assertEquals(1.3f, ef, 0.0001f);
    }

    @Test
    void easyRaisesAndNormalLowersEaseFactor() {
        assertTrue(srs.calculate(2.5f, 1, 0, SrsService.GRADE_EASY, now).easeFactor() > 2.5f);
        assertTrue(srs.calculate(2.5f, 1, 0, SrsService.GRADE_NORMAL, now).easeFactor() < 2.5f);
    }

    @Test
    void invalidGradeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> srs.calculate(2.5f, 1, 0, 6, now));
        assertThrows(IllegalArgumentException.class, () -> SrsService.gradeFromRating("meh"));
    }

    @Test
    void ratingMapsToGrade() {
        assertEquals(2, SrsService.gradeFromRating("hard"));
        assertEquals(3, SrsService.gradeFromRating("normal"));
        assertEquals(5, SrsService.gradeFromRating("easy"));
    }
}
