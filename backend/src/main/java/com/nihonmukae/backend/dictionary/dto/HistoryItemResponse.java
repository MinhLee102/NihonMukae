package com.nihonmukae.backend.dictionary.dto;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryItemResponse {
    private Long id;
    private Long wordId; // null nếu từ đã bị xoá khỏi từ điển, hoặc search lúc đó không ra kết quả
    private String kanji; // null cùng điều kiện trên
    private String kana;
    private String rawQuery;
    private ZonedDateTime searchedAt;
}