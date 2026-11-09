package com.nihonmukae.backend.dictionary;

import com.nihonmukae.backend.dictionary.DictionaryRepository.IdPage;
import com.nihonmukae.backend.dictionary.dto.DictDtos.Entry;
import com.nihonmukae.backend.dictionary.dto.DictDtos.HistoryItem;
import com.nihonmukae.backend.dictionary.dto.DictDtos.PageData;
import com.nihonmukae.backend.dictionary.dto.DictDtos.SearchResult;
import com.nihonmukae.backend.shared.dto.PageResponse;
import com.nihonmukae.backend.shared.entity.SearchHistory;
import com.nihonmukae.backend.shared.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Tra từ điển trên các bảng dict_* (JMdict + nghĩa tiếng Việt). Không cache: dữ liệu đổi khi admin sửa hoặc
 * duyệt đóng góp, và các truy vấn đã có chỉ mục nên đủ nhanh.
 */
@Service
@Transactional(readOnly = true)
public class DictionaryService {

    private static final int FUZZY_LIMIT = 10;
    private static final int MAX_QUERY_LENGTH = 100;

    public static final String MATCH_WORD_FORM = "word_form";
    public static final String MATCH_MEANING = "meaning";
    public static final String MATCH_FUZZY = "fuzzy";
    public static final String MATCH_NONE = "none";

    private final DictionaryRepository repository;
    private final SearchHistoryRepository searchHistoryRepository;

    public DictionaryService(DictionaryRepository repository, SearchHistoryRepository searchHistoryRepository) {
        this.repository = repository;
        this.searchHistoryRepository = searchHistoryRepository;
    }

    /**
     * Thử lần lượt: mặt chữ/cách đọc, rồi nghĩa (Việt, Hán Việt, Anh), rồi gợi ý gần đúng.
     * Chế độ được chọn theo kết quả của chính truy vấn nên các trang sau của cùng một lượt tìm luôn nhất quán.
     */
    public SearchResult search(String rawQuery, Pageable pageable) {
        String q = rawQuery == null ? "" : rawQuery.trim();
        if (q.length() > MAX_QUERY_LENGTH) {
            q = q.substring(0, MAX_QUERY_LENGTH);
        }
        if (q.isEmpty()) {
            return empty(pageable);
        }
        int offset = (int) pageable.getOffset();
        int size = pageable.getPageSize();

        IdPage byForm = repository.searchByForm(DictionaryRepository.kanaVariants(q), offset, size);
        if (byForm.total() > 0) {
            return result(byForm, pageable, MATCH_WORD_FORM);
        }
        IdPage byMeaning = repository.searchByMeaning(q, offset, size);
        if (byMeaning.total() > 0) {
            return result(byMeaning, pageable, MATCH_MEANING);
        }
        if (pageable.getPageNumber() > 0) {
            return empty(pageable);
        }
        List<Long> fuzzy = repository.searchFuzzy(q, FUZZY_LIMIT);
        if (fuzzy.isEmpty()) {
            return empty(pageable);
        }
        List<Entry> entries = repository.load(fuzzy);
        return new SearchResult(new PageData<>(entries, 0, entries.size(), entries.size(), 1), MATCH_FUZZY);
    }

    /** Tra cho popup (kệ sách, đề JLPT): kanji và/hoặc cách đọc; kana không bắt buộc khi đã có kanji. */
    public Entry lookup(String kanji, String kana) {
        boolean hasKanji = kanji != null && !kanji.isBlank();
        boolean hasKana = kana != null && !kana.isBlank();
        if (!hasKanji && !hasKana) {
            throw new IllegalArgumentException("Thiếu tham số 'kanji' hoặc 'kana'");
        }
        Set<String> kanaForms = hasKana ? DictionaryRepository.kanaVariants(kana.trim()) : Set.of();
        Long id = null;
        if (hasKanji) {
            id = repository.findByKanji(Set.of(kanji.trim()), kanaForms);
        }
        if (id == null && hasKana) {
            id = repository.findByKana(kanaForms);
        }
        if (id == null) {
            throw new NotFoundException("Không tìm thấy từ");
        }
        return getEntry(id);
    }

    public Entry getEntry(long id) {
        List<Entry> found = repository.load(List.of(id));
        if (found.isEmpty()) {
            throw new NotFoundException("Không tìm thấy từ");
        }
        return found.get(0);
    }

    private SearchResult result(IdPage page, Pageable pageable, String matchType) {
        List<Entry> entries = repository.load(page.ids());
        int totalPages = (int) Math.ceil(page.total() / (double) pageable.getPageSize());
        return new SearchResult(new PageData<>(entries, pageable.getPageNumber(), pageable.getPageSize(), page.total(),
                totalPages), matchType);
    }

    private SearchResult empty(Pageable pageable) {
        return new SearchResult(new PageData<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0),
                MATCH_NONE);
    }

    /**
     * Ghi lịch sử tìm kiếm, chỉ ở trang đầu để không ghi trùng khi người dùng bấm trang kế.
     * userId null (không đăng nhập) thì bỏ qua.
     */
    @Transactional
    public void recordSearchHistory(UUID userId, String rawQuery, Pageable pageable, SearchResult result) {
        if (userId == null || pageable.getPageNumber() > 0) {
            return;
        }
        List<Entry> content = result.results().content();
        Long wordId = content.isEmpty() ? null : content.get(0).id();
        searchHistoryRepository.save(SearchHistory.builder()
                .userId(userId)
                .wordId(wordId)
                .rawQuery(rawQuery)
                .build());
    }

    // ─── Lịch sử tra cứu ────────────────────────────────────────────────

    public PageResponse<HistoryItem> getHistory(UUID userId, Pageable pageable) {
        Page<SearchHistoryProjection> page = searchHistoryRepository.findHistory(userId, pageable);
        return PageResponse.of(page.map(p -> new HistoryItem(p.getId(), p.getWordId(), p.getKanji(), p.getKana(),
                p.getMeaning(), p.getRawQuery(),
                p.getSearchedAt() == null ? null : p.getSearchedAt().atZone(java.time.ZoneOffset.UTC))));
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
}
