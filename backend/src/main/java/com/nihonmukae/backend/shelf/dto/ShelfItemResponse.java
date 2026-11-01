package com.nihonmukae.backend.shelf.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/** Dùng cho cả danh sách (excerpt, không có tokens) và chi tiết (content và tokens). */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShelfItemResponse {
    private UUID id;
    private String title;
    private String excerpt;
    private String content;
    private List<ShelfToken> tokens;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}
