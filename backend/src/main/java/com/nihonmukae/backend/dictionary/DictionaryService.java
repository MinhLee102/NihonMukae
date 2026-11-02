package com.nihonmukae.backend.dictionary;

import com.nihonmukae.backend.dictionary.dto.ExampleSentenceResponse;
import com.nihonmukae.backend.dictionary.dto.HistoryItemResponse;
import com.nihonmukae.backend.dictionary.dto.SearchResultResponse;
import com.nihonmukae.backend.dictionary.dto.WordLookupResponse;
import com.nihonmukae.backend.dictionary.dto.WordMeaningResponse;
import com.nihonmukae.backend.dictionary.dto.WordResponse;
import com.nihonmukae.backend.shared.dto.PageResponse;
import com.nihonmukae.backend.shared.entity.SearchHistory;
import com.nihonmukae.backend.shared.entity.Word;
import com.nihonmukae.backend.shared.entity.WordMeaning;
import com.nihonmukae.backend.shared.exception.NotFoundException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class DictionaryService {

    private static final int TRIGRAM_FALLBACK_LIMIT = 10;
    private static final int MAX_LOOKUP_EXAMPLES = 2;
    private static final String DEFAULT_LANG = "vi";

    private static final String MATCH_WORD_FORM = "word_form";
    private static final String MATCH_MEANING = "meaning";
    private static final String MATCH_FUZZY = "fuzzy";
    private static final String MATCH_NONE = "none";

    // Ký tự có ý nghĩa đặc biệt trong cú pháp tsquery của Postgres — phải
    // loại bỏ khỏi input người dùng trước khi build tsquery, không thì
    // to_tsquery() sẽ ném lỗi cú pháp (hoặc tệ hơn, bị lợi dụng để inject).
    private static final String TSQUERY_SPECIAL_CHARS_REGEX = "[&|!():'\"*]";

    private final WordRepository wordRepository;
    private final SearchHistoryRepository searchHistoryRepository;

    public DictionaryService(WordRepository wordRepository, SearchHistoryRepository searchHistoryRepository) {
        this.wordRepository = wordRepository;
        this.searchHistoryRepository = searchHistoryRepository;
    }

    /**
     * Chain: word-form (kanji/kana) -> meaning (nghĩa tiếng Việt) -> fuzzy
     * (trigram, gợi ý gần đúng). Chỉ fallback sang bước tiếp theo khi bước
     * trước KHÔNG ra kết quả nào ở trang đầu (page=0).
     *
     * QUAN TRỌNG: method này được @Cacheable (DICT-06) nên PHẢI thuần tuý
     * (pure) — không có side effect. Việc ghi search_history KHÔNG được đặt
     * trong method này, vì lần cache-hit sẽ không chạy thân method, khiến
     * history bị thiếu âm thầm. Ghi history nằm ở recordSearchHistory() —
     * method riêng, Controller luôn gọi bất kể cache hit hay miss.
     */
    @Cacheable(cacheNames = "dictSearch")
    public SearchResultResponse searchCached(String rawQuery, String lang, Pageable pageable) {
        String effectiveLang = (lang == null || lang.isBlank()) ? DEFAULT_LANG : lang;
        String tsQuery = buildPrefixTsQuery(rawQuery);

        if (tsQuery == null) {
            return new SearchResultResponse(PageResponse.of(Page.empty(pageable)), MATCH_NONE);
        }

        Page<Word> wordFormPage = wordRepository.searchFullText(tsQuery, rawQuery.trim(), pageable);
        if (!wordFormPage.isEmpty() || pageable.getPageNumber() > 0) {
            return toSearchResult(wordFormPage, effectiveLang, MATCH_WORD_FORM);
        }

        Page<Word> meaningPage = wordRepository.searchByMeaning(tsQuery, rawQuery.trim(), pageable);
        if (!meaningPage.isEmpty()) {
            return toSearchResult(meaningPage, effectiveLang, MATCH_MEANING);
        }

        return searchFuzzyFallback(rawQuery, effectiveLang);
    }

    /**
     * Ghi lịch sử tìm kiếm — Controller gọi riêng, LUÔN LUÔN (không phụ
     * thuộc searchCached() cache hit hay miss). Chỉ ghi ở trang đầu
     * (page=0), tránh ghi trùng khi người dùng bấm next page của cùng 1
     * lượt search. userId null (JWT không có/không hợp lệ) thì bỏ qua.
     */
    @Transactional
    public void recordSearchHistory(UUID userId, String rawQuery, Pageable pageable, SearchResultResponse result) {
        if (userId == null || pageable.getPageNumber() > 0) {
            return;
        }
        Long wordId = result.getResults().getContent().isEmpty()
                ? null
                : result.getResults().getContent().get(0).getId();
        searchHistoryRepository.save(SearchHistory.builder()
                .userId(userId)
                .wordId(wordId)
                .rawQuery(rawQuery)
                .build());
    }

    private SearchResultResponse toSearchResult(Page<Word> page, String lang, String matchType) {
        PageResponse<WordResponse> pageResponse =
                PageResponse.of(page.map(w -> toWordResponse(w, lang, null)));
        return new SearchResultResponse(pageResponse, matchType);
    }

    private SearchResultResponse searchFuzzyFallback(String rawQuery, String lang) {
        List<Word> fuzzy = wordRepository.searchByTrigramSimilarity(rawQuery.trim(), TRIGRAM_FALLBACK_LIMIT);
        List<WordResponse> content = fuzzy.stream()
                .map(w -> toWordResponse(w, lang, null))
                .collect(Collectors.toList());
        PageResponse<WordResponse> pageResponse = new PageResponse<>(
                content, 0, content.size(), content.size(), content.isEmpty() ? 0 : 1);
        return new SearchResultResponse(pageResponse, content.isEmpty() ? MATCH_NONE : MATCH_FUZZY);
    }

    @Cacheable(cacheNames = "dictLookup")
    @Transactional(readOnly = true)
    public WordLookupResponse lookup(String kanji, String kana) {
        boolean hasKanji = kanji != null && !kanji.isBlank();
        boolean hasKana = kana != null && !kana.isBlank();
        if (!hasKanji && !hasKana) {
            throw new IllegalArgumentException("Thiếu tham số 'kanji' hoặc 'kana'");
        }
        List<Word> found = hasKanji
                ? wordRepository.findLookupByKanji(kanji.trim(), hasKana ? kana.trim() : "")
                : wordRepository.findLookupByKanaOnly(kana.trim());
        if (found.isEmpty()) {
            throw new NotFoundException("Không tìm thấy từ");
        }
        return toWordLookupResponse(found.get(0), DEFAULT_LANG);
    }

    // ─── History CRUD ───────────────────────────────────────────────────
    // Controller đảm bảo userId không null trước khi gọi (SecurityConfig
    // bắt buộc JWT cho các endpoint này, không nằm trong permitAll).

    public PageResponse<HistoryItemResponse> getHistory(UUID userId, Pageable pageable) {
        Page<SearchHistoryProjection> page = searchHistoryRepository.findHistory(userId, pageable);
        return PageResponse.of(page.map(this::toHistoryItemResponse));
    }

    @Transactional
    public void deleteHistoryItem(UUID userId, Long historyId) {
        long deleted = searchHistoryRepository.deleteByIdAndUserId(historyId, userId);
        if (deleted == 0) {
            throw new NotFoundException("Không tìm thấy mục lịch sử này");
        }
    }

    @Transactional
    public void clearHistory(UUID userId) {
        searchHistoryRepository.deleteByUserId(userId);
    }

    private HistoryItemResponse toHistoryItemResponse(SearchHistoryProjection p) {
        return HistoryItemResponse.builder()
                .id(p.getId())
                .wordId(p.getWordId())
                .kanji(p.getKanji())
                .kana(p.getKana())
                .rawQuery(p.getRawQuery())
                .searchedAt(p.getSearchedAt() == null ? null : p.getSearchedAt().atZone(java.time.ZoneOffset.UTC))
                .build();
    }

    /**
     * Sanitize input người dùng rồi build chuỗi tsquery dạng
     * "tok1:* & tok2:*" — mỗi token được match kiểu prefix (bắt đầu bằng).
     * Dùng chung cho cả word-form search lẫn meaning search.
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
