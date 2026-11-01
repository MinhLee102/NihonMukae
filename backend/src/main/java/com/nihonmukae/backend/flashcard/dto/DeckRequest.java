package com.nihonmukae.backend.flashcard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DeckRequest {
    @NotBlank(message = "Tên bộ flashcard không được để trống")
    @Size(max = 200, message = "Tên bộ flashcard tối đa 200 ký tự")
    private String name;

    private String description;

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Màu phải có dạng #RRGGBB")
    private String color;

    @Size(max = 50)
    private String icon;
}
