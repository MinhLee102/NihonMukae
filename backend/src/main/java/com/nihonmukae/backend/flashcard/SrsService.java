package com.nihonmukae.backend.flashcard;

import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;

/**
 * Thuật toán SM-2, viết thuần Java không phụ thuộc thư viện ngoài.
 * Grade: 0..5, dưới 3 coi như quên (reset chuỗi nhớ), từ 3 trở lên là nhớ.
 */
@Service
public class SrsService {

    public static final int GRADE_HARD = 2;
    public static final int GRADE_NORMAL = 3;
    public static final int GRADE_EASY = 5;

    private static final float MIN_EASE_FACTOR = 1.3f;

    public record SrsState(float easeFactor, int intervalDays, int repetitions, ZonedDateTime nextReviewAt) {
    }

    public SrsState calculate(float easeFactor, int intervalDays, int repetitions, int grade, ZonedDateTime now) {
        if (grade < 0 || grade > 5) {
            throw new IllegalArgumentException("Grade phải nằm trong khoảng 0..5");
        }

        int newRepetitions;
        int newInterval;
        if (grade < 3) {
            newRepetitions = 0;
            newInterval = 1;
        } else {
            if (repetitions == 0) {
                newInterval = 1;
            } else if (repetitions == 1) {
                newInterval = 6;
            } else {
                newInterval = Math.round(intervalDays * easeFactor);
            }
            newRepetitions = repetitions + 1;
        }

        int q = 5 - grade;
        float newEase = easeFactor + (0.1f - q * (0.08f + q * 0.02f));
        newEase = Math.max(MIN_EASE_FACTOR, newEase);

        return new SrsState(newEase, newInterval, newRepetitions, now.plusDays(newInterval));
    }

    public static int gradeFromRating(String rating) {
        return switch (rating == null ? "" : rating.toLowerCase()) {
            case "hard" -> GRADE_HARD;
            case "normal" -> GRADE_NORMAL;
            case "easy" -> GRADE_EASY;
            default -> throw new IllegalArgumentException("Rating phải là hard, normal hoặc easy");
        };
    }
}
