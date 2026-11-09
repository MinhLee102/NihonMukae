package com.nihonmukae.backend.dictionary;

import com.nihonmukae.backend.auth.jwt.JwtUserPrincipal;
import com.nihonmukae.backend.dictionary.dto.DictDtos.ContributionRequest;
import com.nihonmukae.backend.dictionary.dto.DictDtos.Entry;
import com.nihonmukae.backend.dictionary.dto.DictDtos.HistoryItem;
import com.nihonmukae.backend.dictionary.dto.DictDtos.KanjiDetail;
import com.nihonmukae.backend.dictionary.dto.DictDtos.KanjiSummary;
import com.nihonmukae.backend.dictionary.dto.DictDtos.MyContribution;
import com.nihonmukae.backend.dictionary.dto.DictDtos.PageData;
import com.nihonmukae.backend.dictionary.dto.DictDtos.SearchResult;
import com.nihonmukae.backend.shared.dto.ApiResponse;
import com.nihonmukae.backend.shared.dto.PageResponse;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dictionary")
public class DictionaryController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final DictionaryService dictionaryService;
    private final ContributionService contributionService;
    private final KanjiService kanjiService;

    public DictionaryController(DictionaryService dictionaryService, ContributionService contributionService,
                                KanjiService kanjiService) {
        this.dictionaryService = dictionaryService;
        this.contributionService = contributionService;
        this.kanjiService = kanjiService;
    }

    // GET /dictionary/search?q=地域&page=0&size=20
    // Công khai, nhưng nếu request có JWT hợp lệ đính kèm (không bắt buộc) thì ghi lịch sử theo user đó.
    @GetMapping("/search")
    public ApiResponse<SearchResult> search(
            @RequestParam("q") String q,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            @AuthenticationPrincipal JwtUserPrincipal principal
    ) {
        Pageable pageable = pageable(page, size);
        SearchResult result = dictionaryService.search(q, pageable);
        if (principal != null) {
            dictionaryService.recordSearchHistory(principal.getId(), q, pageable, result);
        }
        return ApiResponse.success(result);
    }

    // GET /dictionary/lookup?kanji=地域&kana=ちいき
    @GetMapping("/lookup")
    public ApiResponse<Entry> lookup(
            @RequestParam(value = "kanji", required = false) String kanji,
            @RequestParam(value = "kana", required = false) String kana
    ) {
        return ApiResponse.success(dictionaryService.lookup(kanji, kana));
    }

    // ─── Hán tự (công khai) ─────────────────────────────────────────────

    // GET /dictionary/kanji/search?q=食  (hoặc Hán Việt, nghĩa, cách đọc)
    @GetMapping("/kanji/search")
    public ApiResponse<List<KanjiSummary>> searchKanji(@RequestParam("q") String q) {
        return ApiResponse.success(kanjiService.search(q));
    }

    // GET /dictionary/kanji/食
    @GetMapping("/kanji/{kanji}")
    public ApiResponse<KanjiDetail> kanji(@PathVariable String kanji) {
        return ApiResponse.success(kanjiService.detail(kanji));
    }

    // ─── Đóng góp (cần tài khoản đã duyệt) ──────────────────────────────

    // POST /dictionary/entries/{id}/contributions  { kind, ja, vi }
    @PostMapping("/entries/{id}/contributions")
    public ApiResponse<MyContribution> contribute(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @PathVariable long id,
            @RequestBody ContributionRequest req
    ) {
        return ApiResponse.success(contributionService.submit(principal.getId(), id, req),
                "Đã gửi, đóng góp sẽ hiện sau khi admin duyệt");
    }

    @GetMapping("/contributions/mine")
    public ApiResponse<PageData<MyContribution>> myContributions(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size
    ) {
        return ApiResponse.success(contributionService.mine(principal.getId(), page, size));
    }

    // ─── Lịch sử tra cứu ────────────────────────────────────────────────

    // Bắt buộc JWT (không nằm trong permitAll của SecurityConfig)
    @GetMapping("/history")
    public ApiResponse<PageResponse<HistoryItem>> getHistory(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size
    ) {
        return ApiResponse.success(dictionaryService.getHistory(principal.getId(), pageable(page, size)));
    }

    @DeleteMapping("/history/{id}")
    public ApiResponse<Void> deleteHistoryItem(
            @AuthenticationPrincipal JwtUserPrincipal principal,
            @PathVariable Long id
    ) {
        dictionaryService.deleteHistoryItem(principal.getId(), id);
        return ApiResponse.success(null, "Đã xoá");
    }

    @DeleteMapping("/history")
    public ApiResponse<Void> clearHistory(@AuthenticationPrincipal JwtUserPrincipal principal) {
        dictionaryService.clearHistory(principal.getId());
        return ApiResponse.success(null, "Đã xoá toàn bộ lịch sử");
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}
