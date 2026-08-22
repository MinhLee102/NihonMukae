package com.nihonmukae.backend.dictionary.dto;

import com.nihonmukae.backend.shared.dto.PageResponse;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Getter;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultResponse {
    private PageResponse<WordResponse> results;

    // "word_form": khớp trực tiếp kanji/kana (kết quả tốt nhất)
    // "meaning": không khớp mặt chữ, nhưng khớp theo nghĩa tiếng Việt
    // "fuzzy": không khớp chính xác cả 2 kiểu trên, đây là gợi ý gần đúng
    //          (trigram) — FE nên hiện "Có phải bạn muốn tìm..."
    // "none": không có kết quả nào
    private String matchType;
}

