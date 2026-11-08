package com.nihonmukae.backend.practice;

import com.nihonmukae.backend.practice.dto.ResultDtos.Result;
import com.nihonmukae.backend.practice.dto.ResultDtos.ResultQuestion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShelfTextBuilderTest {

    @Test
    void stripsInlineMarkup() {
        assertEquals("この漢字の読み方は？", ShelfTextBuilder.plain("この<u>漢字</u>の**読み方**は？"));
    }

    @Test
    void imageBecomesPlaceholder() {
        assertEquals("（図）", ShelfTextBuilder.plain("[[img:/media/abc]]"));
    }

    @Test
    void tableBecomesPlainRows() {
        String table = "| 名前 | 日 |\n|---|---|\n| 田中 | 月 |";
        assertEquals("名前　日\n田中　月", ShelfTextBuilder.plain(table));
    }

    @Test
    void boxAndAlignmentMarkersAreDropped() {
        assertEquals("お知らせ", ShelfTextBuilder.plain("[[box]][[center]]お知らせ[[/center]][[/box]]"));
    }

    private static ResultQuestion question(int position, String section, int mondai, String text) {
        return new ResultQuestion(position, 1, (long) position, "t", "t", section, null, text,
                List.of("あ", "い", "う", "え"), null, "A", "A", true, null, 1, mondai, null, null, null);
    }

    private static Result result(List<ResultQuestion> questions) {
        return new Result(null, "exam", 1, "N1", null, null, 0, questions.size(), 0, null, List.of(), questions,
                List.of(), null);
    }

    @Test
    void shortSelectionStaysOneUnlabelledItem() {
        Result r = result(List.of(question(1, "moji", 1, "漢字"), question(2, "dokkai", 8, "文章")));
        List<ShelfTextBuilder.Part> parts = ShelfTextBuilder.split(r, List.of(1, 2), 20000);
        assertEquals(1, parts.size());
        assertNull(parts.get(0).label());
    }

    @Test
    void longSelectionSplitsByPartThenByMondaiWithinTheLimit() {
        List<ResultQuestion> qs = new ArrayList<>();
        String longText = "長".repeat(300);
        int position = 1;
        for (int mondai = 1; mondai <= 4; mondai++) {
            for (int i = 0; i < 3; i++) {
                qs.add(question(position++, "bunpou", mondai, longText));
            }
        }
        qs.add(question(position++, "dokkai", 8, longText));
        qs.add(question(position, "chokai", 1, longText));
        Result r = result(qs);
        List<Integer> all = qs.stream().map(ResultQuestion::position).toList();

        List<ShelfTextBuilder.Part> parts = ShelfTextBuilder.split(r, all, 2000);

        assertTrue(parts.stream().allMatch(p -> p.text().length() <= 2000));
        assertEquals(List.of("Ngôn ngữ 1", "Ngôn ngữ 2", "Ngôn ngữ 3", "Ngôn ngữ 4", "Đọc hiểu", "Nghe hiểu"),
                parts.stream().map(ShelfTextBuilder.Part::label).toList());
        // Mỗi 問題 (3 câu, ~1000 ký tự) nằm trọn trong một mục, không bị cắt ngang
        assertTrue(parts.get(0).text().startsWith("【1番】") && parts.get(0).text().contains("【3番】")
                && !parts.get(0).text().contains("【4番】"));
    }
}
