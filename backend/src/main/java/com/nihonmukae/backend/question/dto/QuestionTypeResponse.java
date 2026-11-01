package com.nihonmukae.backend.question.dto;

/** Dạng bài kèm số câu đang có trong kho cho level được hỏi (để màn chọn ôn tập giới hạn số câu). */
public record QuestionTypeResponse(Short id, String code, String nameVi, String nameJa, String section,
                                   boolean groupBased, long available) {
}
