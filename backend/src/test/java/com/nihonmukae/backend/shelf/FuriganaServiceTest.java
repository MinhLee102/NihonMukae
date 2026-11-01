package com.nihonmukae.backend.shelf;

import com.nihonmukae.backend.shelf.dto.ShelfToken;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuriganaServiceTest {

    private final FuriganaService service = new FuriganaService();

    @Test
    void okuriganaDoesNotGetFurigana() {
        List<ShelfToken.Part> parts = FuriganaService.alignRuby("食べる", "たべる");
        assertEquals(2, parts.size());
        assertEquals("食", parts.get(0).text());
        assertEquals("た", parts.get(0).ruby());
        assertEquals("べる", parts.get(1).text());
        assertNull(parts.get(1).ruby());
    }

    @Test
    void pureKanjiWordGetsWholeReading() {
        List<ShelfToken.Part> parts = FuriganaService.alignRuby("日本語", "にほんご");
        assertEquals(1, parts.size());
        assertEquals("にほんご", parts.get(0).ruby());
    }

    @Test
    void katakanaConvertsToHiragana() {
        assertEquals("にほんご", FuriganaService.katakanaToHiragana("ニホンゴ"));
        assertEquals("らーめん", FuriganaService.katakanaToHiragana("ラーメン"));
    }

    @Test
    void annotatesKanjiAndLeavesKanaAlone() {
        List<ShelfToken> tokens = service.annotate("日本語を勉強する。");
        ShelfToken nihongo = tokens.get(0);
        assertEquals("日本語", nihongo.parts().get(0).text());
        assertEquals("にほんご", nihongo.parts().get(0).ruby());
        assertTrue(nihongo.lookup());

        ShelfToken particle = tokens.get(1);
        assertEquals("を", particle.parts().get(0).text());
        assertNull(particle.reading());
        assertFalse(particle.lookup());
    }

    @Test
    void inflectedVerbReportsDictionaryForm() {
        List<ShelfToken> tokens = service.annotate("食べました");
        ShelfToken verb = tokens.get(0);
        assertEquals("食べる", verb.base());
        assertTrue(verb.lookup());
    }

    @Test
    void joinedSurfaceReproducesOriginalText() {
        String text = "お客様は神様です。\n2025年に行った。";
        StringBuilder sb = new StringBuilder();
        for (ShelfToken t : service.annotate(text)) {
            t.parts().forEach(p -> sb.append(p.text()));
        }
        assertEquals(text, sb.toString());
    }
}
