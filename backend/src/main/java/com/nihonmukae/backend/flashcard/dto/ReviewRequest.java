package com.nihonmukae.backend.flashcard.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReviewRequest {
    // hard, normal hoặc easy (tương ứng nút Khó, Bình thường, Dễ)
    @NotBlank(message = "Thiếu kết quả ôn tập")
    private String rating;
}
