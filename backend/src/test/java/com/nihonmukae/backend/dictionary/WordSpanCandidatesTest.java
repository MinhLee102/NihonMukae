package com.nihonmukae.backend.dictionary;

import com.atilika.kuromoji.ipadic.Token;
import com.nihonmukae.backend.shared.nlp.KuromojiHolder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WordSpanCandidatesTest {

    private static List<Token> tokenize(String text) {
        return KuromojiHolder.get().tokenize(text);
    }

    private static int indexOf(List<Token> tokens, String surface) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).getSurface().equals(surface)) {
                return i;
            }
        }
        throw new AssertionError("không có token " + surface);
    }

    private static String surfaceOf(String text, List<Token> tokens, WordSpanService.Span span) {
        return text.substring(tokens.get(span.from()).getPosition(),
                tokens.get(span.to()).getPosition() + tokens.get(span.to()).getSurface().length());
    }

    @Test
    void longestSpanComesFirstAndContainsTheClickedToken() {
        String text = "今回、私が紹介したい映画は『容疑者Xの献身』という作品です。";
        List<Token> tokens = tokenize(text);
        int click = indexOf(tokens, "容疑");
        List<WordSpanService.Span> spans = WordSpanService.candidates(tokens, click);

        assertFalse(spans.isEmpty());
        String top = surfaceOf(text, tokens, spans.get(0));
        assertTrue(top.contains("容疑"), top);
        // mọi khoảng đều chứa token được bấm và có từ hai token trở lên
        for (WordSpanService.Span s : spans) {
            assertTrue(s.from() <= click && click <= s.to());
            assertTrue(s.from() < s.to());
        }
        // 容疑者 nằm trong danh sách ứng viên
        assertTrue(spans.stream().anyMatch(s -> surfaceOf(text, tokens, s).equals("容疑者")));
    }

    @Test
    void spanDoesNotCrossPunctuation() {
        String text = "彼は医者、学生です。";
        List<Token> tokens = tokenize(text);
        int click = indexOf(tokens, "医者");
        for (WordSpanService.Span s : WordSpanService.candidates(tokens, click)) {
            assertFalse(surfaceOf(text, tokens, s).contains("、"));
        }
    }

    @Test
    void spanNeverStartsWithAParticleUnlessClicked() {
        String text = "私の友達が来た";
        List<Token> tokens = tokenize(text);
        int click = indexOf(tokens, "友達");
        for (WordSpanService.Span s : WordSpanService.candidates(tokens, click)) {
            assertEquals(false, "助詞".equals(tokens.get(s.from()).getPartOfSpeechLevel1()));
        }
    }
}
