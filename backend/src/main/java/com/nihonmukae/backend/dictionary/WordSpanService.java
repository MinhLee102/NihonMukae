package com.nihonmukae.backend.dictionary;

import com.atilika.kuromoji.ipadic.Token;
import com.nihonmukae.backend.dictionary.dto.DictDtos.Entry;
import com.nihonmukae.backend.dictionary.dto.DictDtos.Meaning;
import com.nihonmukae.backend.dictionary.dto.DictDtos.SpanLookup;
import com.nihonmukae.backend.dictionary.dto.DictDtos.SpanPart;
import com.nihonmukae.backend.shared.exception.NotFoundException;
import com.nihonmukae.backend.shared.nlp.KuromojiHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Tra từ điển theo vị trí bấm trong một đoạn văn: tìm chuỗi DÀI NHẤT chứa token được bấm mà có trong từ điển,
 * để 容疑者 (Kuromoji tách thành 容疑 + 者) hay 食べている ra đúng một từ. Chuỗi luôn bắt đầu và kết thúc ở ranh giới
 * token, và không bắc qua dấu câu.
 */
@Service
@Transactional(readOnly = true)
public class WordSpanService {

    static final int MAX_TEXT = 120;
    private static final int BACK = 3;
    private static final int FORWARD = 6;
    private static final int MAX_SPAN_CHARS = 20;
    private static final int MAX_PARTS = 6;
    private static final Set<String> FUNCTION_POS = Set.of("助詞", "助動詞");

    private final DictionaryRepository repository;
    private final DictionaryService dictionaryService;
    private final Deinflector deinflector;

    public WordSpanService(DictionaryRepository repository, DictionaryService dictionaryService,
                           Deinflector deinflector) {
        this.repository = repository;
        this.dictionaryService = dictionaryService;
        this.deinflector = deinflector;
    }

    /** Khoảng token [from, to] (chỉ số trong danh sách token), from và to đều tính. */
    record Span(int from, int to) {
    }

    public SpanLookup lookupAt(String text, int offset) {
        if (text == null || text.isBlank() || text.length() > MAX_TEXT || offset < 0 || offset >= text.length()) {
            throw new IllegalArgumentException("Văn bản hoặc vị trí không hợp lệ");
        }
        List<Token> tokens = KuromojiHolder.get().tokenize(text);
        int click = tokenAt(tokens, offset);
        if (click < 0 || isBreak(tokens.get(click))) {
            throw new NotFoundException("Không tìm thấy từ");
        }

        for (Span span : candidates(tokens, click)) {
            String surface = surfaceOf(tokens, text, span);
            Long id = findSpan(tokens, span, surface);
            if (id != null) {
                return result(tokens, text, span, surface, dictionaryService.getEntry(id));
            }
        }
        Token t = tokens.get(click);
        Long single = findSingle(t);
        if (single == null) {
            throw new NotFoundException("Không tìm thấy từ");
        }
        return new SpanLookup(dictionaryService.getEntry(single), t.getSurface(), t.getPosition(), end(t), List.of());
    }

    /** Các khoảng token có chứa token được bấm và có từ hai token trở lên, dài nhất trước. */
    static List<Span> candidates(List<Token> tokens, int click) {
        int lo = click;
        while (lo > 0 && click - lo < BACK && !isBreak(tokens.get(lo - 1))) {
            lo--;
        }
        int hi = click;
        while (hi < tokens.size() - 1 && hi - click < FORWARD && !isBreak(tokens.get(hi + 1))) {
            hi++;
        }
        List<Span> spans = new ArrayList<>();
        for (int from = lo; from <= click; from++) {
            // Từ không bắt đầu bằng trợ từ hay trợ động từ (trừ khi chính là token được bấm)
            if (from != click && FUNCTION_POS.contains(tokens.get(from).getPartOfSpeechLevel1())) {
                continue;
            }
            for (int to = click; to <= hi; to++) {
                if (from != to && end(tokens.get(to)) - tokens.get(from).getPosition() <= MAX_SPAN_CHARS) {
                    spans.add(new Span(from, to));
                }
            }
        }
        spans.sort(Comparator
                .<Span>comparingInt(s -> end(tokens.get(s.to())) - tokens.get(s.from()).getPosition()).reversed()
                .thenComparing(Comparator.comparingInt(Span::from).reversed()));
        return spans;
    }

    private Long findSpan(List<Token> tokens, Span span, String surface) {
        Token first = tokens.get(span.from());
        Token last = tokens.get(span.to());
        // Khớp nguyên văn: không kết thúc bằng trợ từ (今日は là một mục nhưng không nên nuốt は)
        if (!FUNCTION_POS.contains(last.getPartOfSpeechLevel1())) {
            Long id = findForm(surface);
            if (id != null) {
                return id;
            }
        }
        // Động từ hoặc tính từ chia: 食べている thành 食べる
        String pos = first.getPartOfSpeechLevel1();
        if ("動詞".equals(pos) || "形容詞".equals(pos)) {
            for (String base : deinflector.baseForms(surface)) {
                Long id = findForm(base);
                if (id != null) {
                    return id;
                }
            }
        }
        return null;
    }

    /** Khớp nguyên văn một chuỗi nhiều token: phải có Hán tự, hoặc toàn katakana (tên riêng, từ mượn). */
    private Long findForm(String form) {
        if (containsKanji(form)) {
            return repository.findByKanji(Set.of(form), Set.of());
        }
        if (isKatakana(form)) {
            return repository.findByKana(DictionaryRepository.kanaVariants(form));
        }
        return null;
    }

    /** Tra một token như popup cũ: dạng gốc của Kuromoji, kèm cách đọc khi từ không chia. */
    private Long findSingle(Token token) {
        String surface = token.getSurface();
        String base = "*".equals(token.getBaseForm()) ? surface : token.getBaseForm();
        if (containsKanji(base)) {
            Set<String> kana = Set.of();
            if (surface.equals(base) && token.getReading() != null && !"*".equals(token.getReading())) {
                kana = DictionaryRepository.kanaVariants(DictionaryRepository.toHiragana(token.getReading()));
            }
            return repository.findByKanji(Set.of(base), kana);
        }
        return repository.findByKana(DictionaryRepository.kanaVariants(base));
    }

    private SpanLookup result(List<Token> tokens, String text, Span span, String surface, Entry entry) {
        List<SpanPart> parts = new ArrayList<>();
        for (int i = span.from(); i <= span.to() && parts.size() < MAX_PARTS; i++) {
            Token t = tokens.get(i);
            if (FUNCTION_POS.contains(t.getPartOfSpeechLevel1())) {
                continue;
            }
            Long id = findSingle(t);
            if (id == null) {
                continue;
            }
            String reading = t.getReading() == null || "*".equals(t.getReading()) ? null
                    : DictionaryRepository.toHiragana(t.getReading());
            parts.add(new SpanPart(t.getSurface(), reading, firstMeaning(dictionaryService.getEntry(id))));
        }
        return new SpanLookup(entry, surface, tokens.get(span.from()).getPosition(), end(tokens.get(span.to())),
                parts);
    }

    private static String firstMeaning(Entry e) {
        for (Meaning m : e.meanings()) {
            if ("vi".equals(m.lang())) {
                return m.meaning();
            }
        }
        return e.meanings().isEmpty() ? null : e.meanings().get(0).meaning();
    }

    private static int tokenAt(List<Token> tokens, int offset) {
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (offset >= t.getPosition() && offset < end(t)) {
                return i;
            }
        }
        return -1;
    }

    private static String surfaceOf(List<Token> tokens, String text, Span span) {
        return text.substring(tokens.get(span.from()).getPosition(), end(tokens.get(span.to())));
    }

    private static int end(Token t) {
        return t.getPosition() + t.getSurface().length();
    }

    /** Dấu câu, ký hiệu, khoảng trắng: chuỗi không đi qua các token này. */
    private static boolean isBreak(Token t) {
        return t.getSurface().isBlank() || "記号".equals(t.getPartOfSpeechLevel1());
    }

    static boolean containsKanji(String s) {
        return s.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN || cp == 0x3005);
    }

    static boolean isKatakana(String s) {
        return !s.isEmpty() && s.codePoints().allMatch(
                cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.KATAKANA || cp == 0x30FC);
    }
}
