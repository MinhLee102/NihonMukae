package com.nihonmukae.backend.question.dto;

import java.math.BigDecimal;
import java.util.List;

public final class JlptDtos {

    private JlptDtos() {
    }

    /** 1 đề thi có thể chọn: số câu, tổng thời gian các khối có trong đề, các phần thi có mặt. */
    public record TestSummary(Long id, String title, String sourceNote, Short year, Short month, int questionCount,
                              int durationMin, List<String> sections) {
    }

    /** Một bộ nghe luyện được nguyên bộ: đề có audio cả phần nghe. */
    public record ListeningSetInfo(Long id, String title, int questionCount) {
    }

    public record BlueprintType(Short typeId, String code, String nameVi, String nameJa, int quantity,
                                BigDecimal pointsPerQuestion, long available) {
    }

    public record BlueprintBlock(int blockNo, String name, int durationMin, List<BlueprintType> types) {
    }

    /** Cấu trúc đề thật của 1 level. shortages liệt kê dạng bài mà kho chưa đủ câu cho đề random. */
    public record Blueprint(int level, List<BlueprintBlock> blocks, List<String> shortages) {
    }
}
