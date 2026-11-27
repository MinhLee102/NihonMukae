package com.nihonmukae.backend.grammar;

import com.nihonmukae.backend.grammar.GrammarDuplicates.Point;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrammarDuplicatesTest {

    @Test
    void sameNameOrSameReadingAcrossLevelsIsADuplicate() {
        List<Point> points = List.of(
                new Point(1, 4, "〜終わる", "おわる"),
                new Point(2, 3, "〜おわる", "おわる"),
                new Point(3, 3, "〜ざるを得ない", "ざるをえない"),
                new Point(4, 1, "~ざるを得ない", null));
        List<List<Point>> groups = GrammarDuplicates.groups(points, Set.of());
        assertEquals(2, groups.size());
        // Mỗi nhóm xếp cấp thấp trước (N4 trước N3)
        assertEquals(List.of(1L, 2L), groups.get(0).stream().map(Point::id).toList());
        assertEquals(List.of(3L, 4L), groups.get(1).stream().map(Point::id).toList());
    }

    @Test
    void sameLevelMatchesAreNotReported() {
        List<Point> points = List.of(new Point(1, 3, "〜ように", "ように"), new Point(2, 3, "〜ようにする", "ようにする"),
                new Point(3, 3, "〜ように (mong)", "ように"));
        assertTrue(GrammarDuplicates.groups(points, Set.of()).isEmpty());
    }

    @Test
    void pairsMarkedDistinctAreLeftOut() {
        List<Point> points = List.of(new Point(10, 4, "〜ように", "ように"), new Point(20, 3, "〜ように", "ように"));
        assertEquals(1, GrammarDuplicates.groups(points, Set.of()).size());
        assertTrue(GrammarDuplicates.groups(points, Set.of(GrammarDuplicates.pairKey(20, 10))).isEmpty());
    }

    @Test
    void shortReadingsDoNotMatchByThemselves() {
        // Trợ từ một ký tự: chỉ khớp khi tên giống, không khớp chỉ vì cùng cách đọc
        List<Point> points = List.of(new Point(1, 5, "に (thời điểm)", "に"), new Point(2, 4, "〜に (mục đích)", "に"));
        assertTrue(GrammarDuplicates.groups(points, Set.of()).isEmpty());
    }
}
