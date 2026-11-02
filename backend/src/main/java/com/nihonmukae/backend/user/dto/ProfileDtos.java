package com.nihonmukae.backend.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.ZonedDateTime;
import java.util.UUID;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record Profile(UUID id, String email, String name, String role, Short targetLevel,
                          ZonedDateTime createdAt) {
    }

    /** targetLevel: 1 (N1) đến 5 (N5); null = bỏ chọn. */
    public record UpdateRequest(
            @NotBlank(message = "Tên không được để trống")
            @Size(max = 100, message = "Tên tối đa 100 ký tự")
            String name,
            @Min(value = 1, message = "Cấp độ phải từ N5 đến N1")
            @Max(value = 5, message = "Cấp độ phải từ N5 đến N1")
            Short targetLevel) {
    }
}
