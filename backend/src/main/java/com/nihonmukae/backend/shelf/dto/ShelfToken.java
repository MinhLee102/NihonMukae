package com.nihonmukae.backend.shelf.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 1 từ trong bài đọc sau khi tách bằng kuromoji.
 *
 * @param parts     các đoạn của mặt chữ; đoạn kanji mang furigana (reading), okurigana thì không
 * @param base      dạng gốc từ điển (vd 食べた -> 食べる), dùng để tra nghĩa
 * @param reading   cách đọc hiragana của mặt chữ nguyên token (null nếu không có kanji)
 * @param lookup    true nếu là từ có nghĩa đáng tra (danh từ, động từ, tính từ...), false với trợ từ, dấu câu
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShelfToken(List<Part> parts, String base, String reading, boolean lookup) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Part(String text, String ruby) {
    }
}
