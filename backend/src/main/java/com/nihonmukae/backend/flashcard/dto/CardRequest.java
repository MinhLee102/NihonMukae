package com.nihonmukae.backend.flashcard.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * front/back có thể bỏ trống nếu truyền wordId: khi đó service tự điền từ
 * từ điển (front = kanji + kana, back = nghĩa tiếng Việt đầu tiên).
 */
@Getter
@NoArgsConstructor
public class CardRequest {
    private String front;
    private String back;
    private String imageUrl;
    private Long wordId;
}
