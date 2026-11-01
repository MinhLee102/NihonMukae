package com.nihonmukae.backend.practice;

import com.nihonmukae.backend.practice.ScoreCalculator.Item;
import com.nihonmukae.backend.practice.UnitSelector.Unit;
import com.nihonmukae.backend.question.ExamConfigRepository.ScoringSection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PracticeLogicTest {

    private static Item item(String section, double points, boolean correct) {
        return new Item(section, BigDecimal.valueOf(points), correct);
    }

    private final List<ScoringSection> n3 = List.of(
            new ScoringSection("language", List.of("moji", "goi", "bunpou"), 60, 19),
            new ScoringSection("listening", List.of("chokai"), 60, 19),
            new ScoringSection("reading", List.of("dokkai"), 60, 19));

    @Test
    void sectionScoreIsShareOfPointsTimesMax() {
        List<Item> items = List.of(
                item("moji", 1, true), item("goi", 1, false), item("bunpou", 2, true), item("bunpou", 2, false),
                item("dokkai", 4, true),
                item("chokai", 2, true), item("chokai", 2, true));
        ScoreCalculator.Result r = ScoreCalculator.calculate(items, n3, 95);

        assertTrue(r.complete());
        // language: 3 / 6 x 60 = 30, reading: 60, listening: 60
        assertEquals(30.0, r.sections().stream().filter(s -> s.code().equals("language")).findFirst().orElseThrow().score());
        assertEquals(150.0, r.total());
        assertEquals(180, r.maxTotal());
        assertEquals(Boolean.TRUE, r.passed());
    }

    @Test
    void failsWhenOneSectionIsBelowMinimumEvenIfTotalPasses() {
        List<Item> items = List.of(
                item("moji", 1, true), item("dokkai", 1, true), item("chokai", 100, true),
                item("moji", 99, false));
        // language = 1/100 x 60 = 0.6 (< 19), reading 60, listening 60 -> total 120.6 >= 95 but below min
        ScoreCalculator.Result r = ScoreCalculator.calculate(items, n3, 95);
        assertEquals(Boolean.FALSE, r.passed());
        assertTrue(r.sections().stream().anyMatch(ScoreCalculator.SectionScore::belowMin));
    }

    @Test
    void missingSectionMeansPassIsUnknown() {
        ScoreCalculator.Result r = ScoreCalculator.calculate(List.of(item("moji", 1, true), item("dokkai", 1, true)), n3, 95);
        assertFalse(r.complete());
        assertNull(r.passed());
        assertEquals(120, r.maxTotal());
    }

    @Test
    void selectorTakesExactlyTargetWhenPossible() {
        List<Unit> pool = new ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            pool.add(new Unit(null, List.of(i)));
        }
        List<Unit> chosen = UnitSelector.select(pool, 4, new Random(1));
        assertEquals(4, UnitSelector.countQuestions(chosen));
    }

    @Test
    void selectorTakesEverythingWhenPoolIsTooSmall() {
        List<Unit> pool = List.of(new Unit(null, List.of(1L)), new Unit(5L, List.of(2L, 3L)));
        assertEquals(3, UnitSelector.countQuestions(UnitSelector.select(pool, 50, new Random(1))));
    }

    @Test
    void selectorKeepsWholeGroupsAndOvershootsOnlyWhenForced() {
        // Chỉ có nhóm 3 câu, mục tiêu 2: phải lấy cả nhóm
        List<Unit> pool = List.of(new Unit(9L, List.of(1L, 2L, 3L)));
        List<Unit> chosen = UnitSelector.select(pool, 2, new Random(1));
        assertEquals(1, chosen.size());
        assertEquals(3, UnitSelector.countQuestions(chosen));
    }

    @Test
    void selectorPrefersFittingUnitsOverOvershooting() {
        List<Unit> pool = List.of(new Unit(1L, List.of(1L, 2L, 3L)), new Unit(null, List.of(4L)), new Unit(null, List.of(5L)));
        for (int seed = 0; seed < 20; seed++) {
            assertEquals(2, UnitSelector.countQuestions(UnitSelector.select(pool, 2, new Random(seed))));
        }
    }

    private static com.nihonmukae.backend.shared.entity.PracticeAnswer answer(int position, int block, int type) {
        return com.nihonmukae.backend.shared.entity.PracticeAnswer.builder()
                .position((short) position).blockNo((short) block).typeId((short) type).points(BigDecimal.ONE).build();
    }

    @Test
    void mondaiNumberChangesWithTypeAndRestartsPerBlock() {
        List<com.nihonmukae.backend.shared.entity.PracticeAnswer> answers = List.of(
                answer(1, 1, 1), answer(2, 1, 1), answer(3, 1, 2), answer(4, 1, 2), answer(5, 1, 3),
                answer(6, 2, 7), answer(7, 2, 7), answer(8, 2, 8));
        java.util.Map<Integer, Integer> m = PracticeSessionService.mondaiNumbers(answers);
        assertEquals(1, m.get(1));
        assertEquals(1, m.get(2));
        assertEquals(2, m.get(3));
        assertEquals(2, m.get(4));
        assertEquals(3, m.get(5));
        // a new block starts again from 問題1
        assertEquals(1, m.get(6));
        assertEquals(1, m.get(7));
        assertEquals(2, m.get(8));
    }
}
