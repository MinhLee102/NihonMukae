package com.nihonmukae.backend.shelf.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ShelfItemRequest {
    // Bỏ trống thì lấy dòng đầu của nội dung làm tiêu đề
    @Size(max = 500, message = "Tiêu đề tối đa 500 ký tự")
    private String title;

    @NotBlank(message = "Nội dung không được để trống")
    @Size(max = 20000, message = "Nội dung tối đa 20000 ký tự")
    private String content;
}
