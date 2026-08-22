package com.nihonmukae.backend.dictionary;

import com.nihonmukae.backend.auth.jwt.JwtUserPrincipal;
import com.nihonmukae.backend.dictionary.dto.HistoryItemResponse;
import com.nihonmukae.backend.dictionary.dto.SearchResultResponse;
import com.nihonmukae.backend.dictionary.dto.WordLookupResponse;
import com.nihonmukae.backend.shared.dto.ApiResponse;
import com.nihonmukae.backend.shared.dto.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dictionary")
public class DictionaryController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final DictionaryService dictionaryService;

    public DictionaryController(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    // GET /dictionary/search?q=地域&lang=vi&page=0&size=20
    // Public — nhưng nếu request có JWT hợp lệ đính kèm (không bắt buộc),
    // tự ghi lịch sử theo user đó. Tách 2 lời gọi: searchCached() (được
    // @Cacheable, thuần túy) + recordSearchHistory() (luôn chạy, kể cả khi
    // searchCached() trả từ cache) — xem ghi chú trong DictionaryService.
    @GetMapping("/search")
    public ApiResponse<SearchResultResponse> search(
            @RequestParam("q") String q,
            @RequestParam(value = "lang", required = false) String lang,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);
        SearchResultResponse result = dictionaryService.searchCached(q, lang, pageable);
        if (principal != null) {
            dictionaryService.recordSearchHistory(principal.getId(), q, pageable, result);
        }
        return ApiResponse.success(result);
    }

    // GET /dictionary/lookup?kanji=地域&kana=ちいき
    @GetMapping("/lookup")
    public ApiResponse<WordLookupResponse> lookup(
            @RequestParam(value = "kanji", required = false) String kanji,
            @RequestParam("kana") String kana
    ) {
        return ApiResponse.success(dictionaryService.lookup(kanji, kana));
    }

    // GET /dictionary/history?page=0&size=20 — bắt buộc JWT (không nằm
    // trong permitAll của SecurityConfig, Spring Security tự chặn 401/403
    // nếu thiếu token trước khi vào tới đây).
    @GetMapping("/history")
    public ApiResponse<PageResponse<HistoryItemResponse>> getHistory(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);
        return ApiResponse.success(dictionaryService.getHistory(principal.getId(), pageable));
    }

    // DELETE /dictionary/history/{id} — xoá 1 mục lịch sử của chính user đó
    @DeleteMapping("/history/{id}")
    public ApiResponse<Void> deleteHistoryItem(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @PathVariable Long id
    ) {
        dictionaryService.deleteHistoryItem(principal.getId(), id);
        return ApiResponse.success(null, "Đã xoá");
    }

    // DELETE /dictionary/history — xoá toàn bộ lịch sử của user
    @DeleteMapping("/history")
    public ApiResponse<Void> clearHistory(@AuthenticationPrincipal JwtUserPrincipal principal) {
        dictionaryService.clearHistory(principal.getId());
        return ApiResponse.success(null, "Đã xoá toàn bộ lịch sử");
    }
}
