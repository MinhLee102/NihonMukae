package com.nihonmukae.backend.dictionary;

import com.nihonmukae.backend.dictionary.dto.ExampleSentenceResponse;
import com.nihonmukae.backend.dictionary.dto.SearchResultResponse;
import com.nihonmukae.backend.dictionary.dto.WordLookupResponse;
import com.nihonmukae.backend.dictionary.dto.WordMeaningResponse;
import com.nihonmukae.backend.dictionary.dto.WordResponse;
import com.nihonmukae.backend.shared.dto.PageResponse;
import com.nihonmukae.backend.shared.entity.Word;
import com.nihonmukae.backend.shared.entity.WordMeaning;
import com.nihonmukae.backend.shared.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class DictionaryService {

    private static final int TRIGRAM_FALLBACK_LIMIT = 10;
    private static final int MAX_LOOKUP_EXAMPLES = 2;
    private static final String DEFAULT_LANG = "vi";

    // Ký tự có ý nghĩa đặc biệt trong cú pháp tsquery của Postgres — phải
    // loại bỏ khỏi input người dùng trước khi build tsquery, không thì
    // to_tsquery() sẽ ném lỗi cú pháp (hoặc tệ hơn, bị lợi dụng để inject).
    private static final String TSQUERY_SPECIAL_CHARS_REGEX = "[&|!():'\"*]";

    private final WordRepository wordRepository;

    public DictionaryService(WordRepository wordRepository) {
        this.wordRepository = wordRepository;
    }

    public SearchResultResponse search(String rawQuery, String lang, Pageable pageable) {
        String effectiveLang = (lang == null || lang.isBlank()) ? DEFAULT_LANG : lang;
        String tsQuery = buildPrefixTsQuery(rawQuery);

        if (tsQuery == null) {
            return new SearchResultResponse(PageResponse.of(Page.empty(pageable)), false);
        }

        Page<Word> page = wordRepository.searchFullText(tsQuery, pageable);

        if (page.isEmpty() && pageable.getPageNumber() == 0) {
            return searchFuzzyFallback(rawQuery, effectiveLang);
        }

        PageResponse<WordResponse> pageResponse =
                PageResponse.of(page.map(w -> toWordResponse(w, effectiveLang, null)));
        return new SearchResultResponse(pageResponse, false);
    }

    private SearchResultResponse searchFuzzyFallback(String rawQuery, String lang) {
        List<Word> fuzzy = wordRepository.searchByTrigramSimilarity(rawQuery.trim(), TRIGRAM_FALLBACK_LIMIT);
        List<WordResponse> content = fuzzy.stream()
                .map(w -> toWordResponse(w, lang, null))
                .collect(Collectors.toList());
        PageResponse<WordResponse> pageResponse = new PageResponse<>(
                content, 0, content.size(), content.size(), content.isEmpty() ? 0 : 1);
        return new SearchResultResponse(pageResponse, !content.isEmpty());
    }

    public WordLookupResponse lookup(String kanji, String kana) {
        if (kana == null || kana.isBlank()) {
            throw new IllegalArgumentException("Thiếu tham số 'kana'");
        }
        String normalizedKanji = (kanji == null || kanji.isBlank()) ? null : kanji;
        Word word = wordRepository.findForInlineLookup(normalizedKanji, kana)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy từ"));
        return toWordLookupResponse(word, DEFAULT_LANG);
    }

    /**
     * Sanitize input người dùng rồi build chuỗi tsquery dạng
     * "tok1:* & tok2:*" — mỗi token được match kiểu prefix (bắt đầu bằng),
     * cần thiết vì tiếng Nhật không có khoảng trắng nên 1 từ = 1 token duy
     * nhất trong search_vector.
     */
    private String buildPrefixTsQuery(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll(TSQUERY_SPECIAL_CHARS_REGEX, " ").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        String joined = Arrays.stream(cleaned.split("\\s+"))
                .filter(t -> !t.isEmpty())
                .map(t -> t + ":*")
                .collect(Collectors.joining(" & "));
        return joined.isEmpty() ? null : joined;
    }

    private WordResponse toWordResponse(Word w, String lang, Integer maxExamples) {
        return WordResponse.builder()
                .id(w.getId())
                .kanji(w.getKanji())
                .kana(w.getKana())
                .hanViet(w.getHanViet())
                .pronunciation(w.getPronunciation())
                .pos(w.getPos())
                .jlptLevel(w.getJlptLevel())
                .meanings(filterMeanings(w, lang))
                .examples(mapExamples(w, maxExamples))
                .build();
    }

    private WordLookupResponse toWordLookupResponse(Word w, String lang) {
        return WordLookupResponse.builder()
                .id(w.getId())
                .kanji(w.getKanji())
                .kana(w.getKana())
                .hanViet(w.getHanViet())
                .pronunciation(w.getPronunciation())
                .pos(w.getPos())
                .jlptLevel(w.getJlptLevel())
                .meanings(filterMeanings(w, lang))
                .examples(mapExamples(w, MAX_LOOKUP_EXAMPLES))
                .build();
    }

    private List<WordMeaningResponse> filterMeanings(Word w, String lang) {
        if (w.getMeanings() == null) {
            return List.of();
        }
        return w.getMeanings().stream()
                .filter(m -> lang == null || lang.equals(m.getLang()))
                .sorted(Comparator.comparing(WordMeaning::getSortOrder))
                .map(m -> new WordMeaningResponse(m.getLang(), m.getPos(), m.getMeaning()))
                .collect(Collectors.toList());
    }

    private List<ExampleSentenceResponse> mapExamples(Word w, Integer max) {
        if (w.getExamples() == null) {
            return List.of();
        }
        Stream<ExampleSentenceResponse> stream = w.getExamples().stream()
                .map(e -> new ExampleSentenceResponse(e.getSentence(), e.getTranslation()));
        if (max != null) {
            stream = stream.limit(max);
        }
        return stream.collect(Collectors.toList());
    }
}
