package com.nihonmukae.backend.practice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
