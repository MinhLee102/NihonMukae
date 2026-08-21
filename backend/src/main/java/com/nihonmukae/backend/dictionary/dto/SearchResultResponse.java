package com.nihonmukae.backend.dictionary.dto;

import com.nihonmukae.backend.shared.dto.PageResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SearchResultResponse {
    private PageResponse<WordResponse> results;

    // true nếu search chính xác không ra kết quả nào, đây là kết quả gợi ý
    // gần đúng - FE nên hiện dòng "Có phải bạn muốn tìm..."
    private boolean fuzzyFallback;
}
