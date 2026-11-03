package com.nihonmukae.backend.question;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ImportKeysTest {

    private static final List<String> OPTIONS = List.of("1", "2", "3", "4");

    @Test
    void identicalListeningQuestionsInOneFileGetDistinctKeys() {
        ImportKeys keys = new ImportKeys("t1");
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            seen.add(keys.question("listen_quick", 3, "質問を聞いてください。", OPTIONS, null));
        }
        assertEquals(10, seen.size());
    }

    @Test
    void importingTheSameFileAgainGivesTheSameKeys() {
        ImportKeys first = new ImportKeys("t1");
        ImportKeys second = new ImportKeys("t1");
        for (int i = 0; i < 3; i++) {
            assertEquals(first.question("listen_quick", 3, "質問", OPTIONS, null),
                    second.question("listen_quick", 3, "質問", OPTIONS, null));
        }
    }

    @Test
    void sameQuestionInAnotherTestOrTheBankIsNotTheSame() {
        String a = new ImportKeys("t1").question("kanji_reading", 3, "漢字", OPTIONS, null);
        String b = new ImportKeys("t2").question("kanji_reading", 3, "漢字", OPTIONS, null);
        String c = new ImportKeys("bank").question("kanji_reading", 3, "漢字", OPTIONS, null);
        assertNotEquals(a, b);
        assertNotEquals(a, c);
    }

    @Test
    void emptyGroupsAreNotMergedAndWidthDifferencesAreIgnored() {
        ImportKeys keys = new ImportKeys("t1");
        assertNotEquals(keys.group("listen_integrated", 3, null), keys.group("listen_integrated", 3, null));
        assertEquals(new ImportKeys("t1").question("kanji_reading", 3, "ＡＢ　漢字", OPTIONS, null),
                new ImportKeys("t1").question("kanji_reading", 3, "AB 漢字", OPTIONS, null));
    }

    @Test
    void differentOptionsOrPassageAreDifferentQuestions() {
        ImportKeys keys = new ImportKeys("t1");
        assertNotEquals(keys.question("grammar_choice", 3, "（　）", List.of("a", "b", "c", "d"), null),
                keys.question("grammar_choice", 3, "（　）", List.of("a", "b", "c", "e"), null));
        assertNotEquals(keys.question("reading_short", 3, "内容は？", OPTIONS, "文章A"),
                keys.question("reading_short", 3, "内容は？", OPTIONS, "文章B"));
    }
}
