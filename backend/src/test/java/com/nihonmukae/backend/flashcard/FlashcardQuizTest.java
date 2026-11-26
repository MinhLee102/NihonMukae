package com.nihonmukae.backend.flashcard;

import com.nihonmukae.backend.dictionary.dto.DictDtos.Entry;
import com.nihonmukae.backend.dictionary.dto.DictDtos.Example;
import com.nihonmukae.backend.dictionary.dto.DictDtos.Meaning;
import com.nihonmukae.backend.flashcard.FlashcardQuizService.Item;
import com.nihonmukae.backend.flashcard.dto.QuizDtos.QuizQuestion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlashcardQuizTest {

    private static long nextId = 1;

    private static Entry entry(String kanji, String kana, String meaning, String pos, List<Example> examples) {
        return new Entry(nextId++, kanji, kana, kanji != null ? kanji : kana, List.of(), List.of(), null, pos, (short) 3,
                true, false, List.of(new Meaning("vi", null, meaning)), examples, List.of());
    }

    private static Item item(Entry e) {
        boolean hasKanji = e.kanji() != null;
        return new Item(UUID.randomUUID(), e.headword(), e.meanings().get(0).meaning(), hasKanji ? e.kana() : null,
                List.of(), e.pos(), FlashcardQuizService.cloze(e));
    }

    private static List<Entry> pool() {
        List<Entry> pool = new ArrayList<>();
        String[][] words = {
                {"容器", "ようき", "đồ đựng"}, {"疑問", "ぎもん", "câu hỏi, thắc mắc"}, {"内容", "ないよう", "nội dung"},
                {"美容", "びよう", "làm đẹp"}, {"質問", "しつもん", "câu hỏi"}, {"説明", "せつめい", "giải thích"},
                {"警察", "けいさつ", "cảnh sát"}, {"事件", "じけん", "vụ án"}, {"犯人", "はんにん", "thủ phạm"},
        };
        for (String[] w : words) {
            pool.add(entry(w[0], w[1], w[2], "danh từ", List.of()));
        }
        return pool;
    }

    @Test
    void everyQuestionHasFourDistinctOptionsAndTheRightAnswer() {
        Entry suspicion = entry("容疑", "ようぎ", "nghi vấn, tình nghi", "danh từ",
                List.of(new Example("警察は男を窃盗の容疑で逮捕した。", "Cảnh sát bắt người đàn ông vì nghi trộm cắp.", "ai", null)));
        List<Item> items = new ArrayList<>(List.of(item(suspicion)));
        pool().forEach(e -> items.add(item(e)));

        List<QuizQuestion> qs = FlashcardQuizService.build(items, Set.of("meaning", "reading", "cloze"), 50, pool(),
                pool(), new Random(7));

        assertFalse(qs.isEmpty());
        for (QuizQuestion q : qs) {
            assertEquals(4, q.options().size(), q.toString());
            assertEquals(4, new HashSet<>(q.options()).size(), "đáp án trùng: " + q.options());
            assertTrue(q.answer() >= 0 && q.answer() < 4);
        }
    }

    @Test
    void readingQuestionNeverOffersTheCorrectReadingTwice() {
        Entry suspicion = entry("容疑", "ようぎ", "nghi vấn", "danh từ", List.of());
        List<QuizQuestion> qs = FlashcardQuizService.build(List.of(item(suspicion)), Set.of("reading"), 5, pool(),
                pool(), new Random(1));
        assertEquals(1, qs.size());
        QuizQuestion q = qs.get(0);
        assertEquals("ようぎ", q.options().get(q.answer()));
        assertEquals(1, q.options().stream().filter("ようぎ"::equals).count());
    }

    @Test
    void clozeBlanksTheWordInTheSentence() {
        Entry suspicion = entry("容疑", "ようぎ", "nghi vấn", "danh từ",
                List.of(new Example("男は窃盗の容疑で逮捕された。", "Người đàn ông bị bắt vì nghi trộm cắp.", "ai", null)));
        FlashcardQuizService.Cloze c = FlashcardQuizService.cloze(suspicion);
        assertNotNull(c);
        assertEquals("男は窃盗の（　　）で逮捕された。", c.sentence());
        assertEquals("容疑", c.answer());

        // Câu không chứa nguyên văn từ (đã chia) thì không làm câu điền từ
        Entry eat = entry("食べる", "たべる", "ăn", "động từ",
                List.of(new Example("パンを食べた。", "Tôi đã ăn bánh mì.", "ai", null)));
        assertNull(FlashcardQuizService.cloze(eat));
    }

    @Test
    void overlappingMeaningsAreNotUsedAsWrongAnswers() {
        assertTrue(FlashcardQuizService.overlaps("câu hỏi, thắc mắc", "câu hỏi"));
        assertFalse(FlashcardQuizService.overlaps("nghi vấn, tình nghi", "câu hỏi"));
        List<String> picked = FlashcardQuizService.pick(List.of(List.of("câu hỏi", "câu hỏi, thắc mắc", "nội dung")),
                3, m -> !FlashcardQuizService.overlaps(m, "thắc mắc"), new Random(3));
        assertFalse(picked.contains("câu hỏi, thắc mắc"));
    }

    @Test
    void questionsAreSkippedWhenThereAreNotEnoughWrongAnswers() {
        Entry only = entry("容疑", "ようぎ", "nghi vấn", "danh từ", List.of());
        List<QuizQuestion> qs = FlashcardQuizService.build(List.of(item(only)), Set.of("meaning"), 5, List.of(),
                List.of(), new Random(1));
        assertTrue(qs.isEmpty());
    }
}
