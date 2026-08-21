package com.nihonmukae.backend.dictionary;

import com.nihonmukae.backend.dictionary.dto.SearchResultResponse;
import com.nihonmukae.backend.dictionary.dto.WordLookupResponse;
import com.nihonmukae.backend.shared.dto.ApiResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
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
    @GetMapping("/search")
    public ApiResponse<SearchResultResponse> search(
            @RequestParam("q") String q,
            @RequestParam(value = "lang", required = false) String lang,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);
        return ApiResponse.success(dictionaryService.search(q, lang, pageable));
    }

    // GET /dictionary/lookup?kanji=地域&kana=ちいき
    @GetMapping("/lookup")
    public ApiResponse<WordLookupResponse> lookup(
            @RequestParam(value = "kanji", required = false) String kanji,
            @RequestParam("kana") String kana
    ) {
        return ApiResponse.success(dictionaryService.lookup(kanji, kana));
    }
}
