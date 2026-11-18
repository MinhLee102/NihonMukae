package com.nihonmukae.backend.dictionary;

import com.nihonmukae.backend.dictionary.dto.DictDtos.Entry;
import com.nihonmukae.backend.dictionary.dto.DictDtos.Meaning;
import com.nihonmukae.backend.dictionary.dto.DictDtos.SpanLookup;
import com.nihonmukae.backend.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Logic chọn từ dài nhất, với kho từ điển giả (không cần database). */
class WordSpanServiceTest {

    private DictionaryRepository repository;
    private DictionaryService dictionaryService;
    private WordSpanService service;

    private static Entry entry(long id, String kanji, String meaning) {
        return new Entry(id, kanji, kanji, kanji, List.of(), List.of(), null, null, null, true, false,
                List.of(new Meaning("vi", null, meaning)), List.of(), List.of());
    }

    @BeforeEach
    void setUp() {
        repository = mock(DictionaryRepository.class);
        dictionaryService = mock(DictionaryService.class);
        service = new WordSpanService(repository, dictionaryService, new Deinflector());
        when(repository.findByKanji(anySet(), anySet())).thenReturn(null);
        when(repository.findByKana(anySet())).thenReturn(null);
    }

    private void word(String form, long id, String meaning) {
        when(repository.findByKanji(eq(Set.of(form)), anySet())).thenReturn(id);
        when(dictionaryService.getEntry(id)).thenReturn(entry(id, form, meaning));
    }

    @Test
    void clickingTheFirstPartFindsTheWholeCompound() {
        word("容疑者", 1, "nghi phạm");
        word("容疑", 2, "nghi vấn");
        word("者", 3, "người");
        String text = "映画は『容疑者Xの献身』という作品です。";

        SpanLookup r = service.lookupAt(text, text.indexOf("容疑"));

        assertEquals("容疑者", r.surface());
        assertEquals(1L, r.entry().id());
        assertEquals(text.indexOf("容疑者"), r.start());
        assertEquals(text.indexOf("容疑者") + 3, r.end());
        assertTrue(r.parts().size() >= 2, "phải liệt kê các thành phần");
    }

    @Test
    void clickingTheSuffixAlsoFindsTheWholeCompound() {
        word("容疑者", 1, "nghi phạm");
        word("者", 3, "người");
        String text = "映画は『容疑者Xの献身』という作品です。";

        SpanLookup r = service.lookupAt(text, text.indexOf("者"));

        assertEquals("容疑者", r.surface());
    }

    @Test
    void doesNotSwallowAFollowingParticle() {
        word("今日は", 1, "xin chào");
        word("今日", 2, "hôm nay");
        String text = "今日は雨です。";

        SpanLookup r = service.lookupAt(text, 0);

        assertEquals("今日", r.surface());
        assertEquals(2L, r.entry().id());
    }

    @Test
    void inflectedVerbChainResolvesToTheDictionaryForm() {
        word("食べる", 5, "ăn");
        String text = "毎日ご飯を食べている。";

        SpanLookup r = service.lookupAt(text, text.indexOf("食"));

        assertEquals(5L, r.entry().id());
        assertEquals("食べている", r.surface());
    }

    @Test
    void unknownWordIsNotFound() {
        assertThrows(NotFoundException.class, () -> service.lookupAt("ああ。", 0));
    }

    @Test
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> service.lookupAt("", 0));
        assertThrows(IllegalArgumentException.class, () -> service.lookupAt("日本語", 5));
        assertThrows(IllegalArgumentException.class, () -> service.lookupAt("あ".repeat(121), 0));
    }
}
