package com.nihonmukae.backend.grammar;

import com.atilika.kuromoji.ipadic.Token;
import com.nihonmukae.backend.grammar.GrammarMatcher.Hit;
import com.nihonmukae.backend.grammar.GrammarMatcher.Point;
import com.nihonmukae.backend.shared.nlp.KuromojiHolder;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrammarMatcherTest {

    private static final GrammarMatcher MATCHER = new GrammarMatcher(List.of(
            new Point(1, "〜ざるを得ない", "ざるをえない"),
            new Point(2, "〜ば〜ほど", "ばほど"),
            new Point(3, "〜ても", "ても"),
            new Point(4, "〜ことにする", "ことにする"),
            new Point(5, "〜らしい", "らしい"),
            new Point(6, "〜に違いない", "にちがいない"),
            new Point(7, "〜は", "は"),
            new Point(8, "〜こと", "こと"),
            new Point(9, "〜にあって / 〜にあっても", "にあって")));

    private static List<Long> ids(String text) {
        Set<Integer> starts = new HashSet<>();
        for (Token t : KuromojiHolder.get().tokenize(text)) {
            starts.add(t.getPosition());
        }
        starts.add(text.length());
        return MATCHER.find(text, starts).stream().map(Hit::id).toList();
    }

    @Test
    void conjugatedEndingsStillMatch() {
        assertEquals(List.of(1L), ids("試合を中止せざるを得なかった。"));
        assertEquals(List.of(1L), ids("引き受けざるを得ません。"));
        assertEquals(List.of(4L), ids("毎朝走ることにした。"));
        assertEquals(List.of(5L), ids("彼は来ないらしかった。").stream().filter(i -> i != 7L).toList());
    }

    @Test
    void piecesMayHaveWordsBetweenThem() {
        assertEquals(List.of(2L), ids("読めば読むほど面白い。"));
    }

    @Test
    void kanaReadingAndAlternativeFormsMatch() {
        assertEquals(List.of(6L), ids("あの人が犯人にちがいない。"));
        assertEquals(List.of(9L), ids("この困難な時代にあっても希望を持つ。"));
    }

    @Test
    void shortPatternsNeedWholeWords() {
        // ても inside とても is not the grammar ても
        assertTrue(!ids("とても暑い。").contains(3L));
        assertTrue(ids("雨が降っても行く。").contains(3L));
    }

    @Test
    void singleCharacterPatternsAreSkippedAndContainedHitsDropped() {
        List<Long> found = ids("毎朝走ることにした。");
        assertTrue(!found.contains(7L));
        // こと sits inside ことにした, so only the longer pattern is reported
        assertTrue(!found.contains(8L));
    }

    @Test
    void piecesDropNotesAndPlaceholders() {
        assertEquals(List.of("ざるを得ない"), GrammarMatcher.pieces("Vない(bỏ ない) + ざるを得ない"));
        assertEquals(List.of("ば", "ほど"), GrammarMatcher.pieces("～ば～ほど"));
    }
}
