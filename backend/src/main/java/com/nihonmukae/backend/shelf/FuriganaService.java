package com.nihonmukae.backend.shelf;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;
import com.nihonmukae.backend.shelf.dto.ShelfToken;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tách văn bản tiếng Nhật thành từ và gắn furigana cho phần kanji bằng kuromoji.
 * Chạy đồng bộ trong request lưu bài (không qua RabbitMQ), văn bản vài nghìn chữ
 * xử lý trong dưới 1 giây.
 */
@Service
public class FuriganaService {

    // Từ loại không đáng tra nghĩa khi click
    private static final Set<String> SKIP_POS = Set.of("助詞", "助動詞", "記号", "フィラー", "その他");

    // Tokenizer khởi tạo nặng (nạp từ điển ipadic) nên dùng chung 1 instance, thread-safe
    private final Tokenizer tokenizer = new Tokenizer();

    public List<ShelfToken> annotate(String text) {
        List<ShelfToken> result = new ArrayList<>();
        for (Token token : tokenizer.tokenize(text)) {
            result.add(toShelfToken(token));
        }
        return result;
    }

    private ShelfToken toShelfToken(Token token) {
        String surface = token.getSurface();
        boolean lookup = !SKIP_POS.contains(token.getPartOfSpeechLevel1()) && !surface.isBlank();
        String base = "*".equals(token.getBaseForm()) ? surface : token.getBaseForm();

        String reading = null;
        List<ShelfToken.Part> parts;
        if (containsKanji(surface) && token.getReading() != null && !"*".equals(token.getReading())) {
            reading = katakanaToHiragana(token.getReading());
            parts = alignRuby(surface, reading);
        } else {
            parts = List.of(new ShelfToken.Part(surface, null));
        }
        return new ShelfToken(parts, base, reading, lookup);
    }

    /**
     * Chia mặt chữ thành các đoạn kanji và kana, rồi gióng với cách đọc để chỉ đặt
     * furigana lên phần kanji (食べる + たべる -> 食 mang "た", "べる" giữ nguyên).
     * Gióng không được thì đặt furigana lên cả token.
     */
    static List<ShelfToken.Part> alignRuby(String surface, String reading) {
        List<String> segments = splitByScript(surface);
        StringBuilder regex = new StringBuilder("^");
        for (String seg : segments) {
            if (containsKanji(seg)) {
                regex.append("(.+?)");
            } else {
                regex.append(Pattern.quote(katakanaToHiragana(seg)));
            }
        }
        regex.append("$");

        Matcher m = Pattern.compile(regex.toString()).matcher(reading);
        if (!m.matches()) {
            return List.of(new ShelfToken.Part(surface, reading));
        }
        List<ShelfToken.Part> parts = new ArrayList<>();
        int group = 1;
        for (String seg : segments) {
            parts.add(containsKanji(seg)
                    ? new ShelfToken.Part(seg, m.group(group++))
                    : new ShelfToken.Part(seg, null));
        }
        return parts;
    }

    private static List<String> splitByScript(String s) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean currentKanji = false;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            boolean kanji = isKanji(cp);
            if (current.length() > 0 && kanji != currentKanji) {
                segments.add(current.toString());
                current.setLength(0);
            }
            current.appendCodePoint(cp);
            currentKanji = kanji;
            i += Character.charCount(cp);
        }
        if (current.length() > 0) {
            segments.add(current.toString());
        }
        return segments;
    }

    private static boolean isKanji(int cp) {
        // 々 (0x3005) lặp kanji, coi như kanji để mang furigana theo
        return Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN || cp == 0x3005;
    }

    private static boolean containsKanji(String s) {
        return s.codePoints().anyMatch(FuriganaService::isKanji);
    }

    static String katakanaToHiragana(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            // Katakana ァ..ヶ (0x30A1..0x30F6) lệch hiragana 0x60; giữ nguyên ー và ký tự khác
            sb.append(c >= 0x30A1 && c <= 0x30F6 ? (char) (c - 0x60) : c);
        }
        return sb.toString();
    }
}
