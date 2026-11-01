package com.nihonmukae.backend.practice;

import com.nihonmukae.backend.question.ExamConfigRepository.ScoringSection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Điểm ước tính theo thang JLPT: điểm phần = tổng điểm các câu đúng / tổng điểm tất cả câu thuộc phần
 * trong phiên x điểm tối đa của phần. Chỉ là ước tính, JLPT thật dùng điểm quy đổi.
 */
public final class ScoreCalculator {

    /** 1 câu trong phiên: thuộc phần thi nào, giá trị bao nhiêu điểm, có làm đúng không. */
    public record Item(String section, BigDecimal points, boolean correct) {
    }

    public record SectionScore(String code, double score, int max, int min, boolean belowMin) {
    }

    /**
     * @param passed null khi phiên không đủ các phần tính điểm (vd đề chưa có câu nghe) nên không thể kết luận đạt hay chưa
     */
    public record Result(double total, int maxTotal, Integer passScore, Boolean passed, boolean complete,
                         List<SectionScore> sections) {
    }

    private ScoreCalculator() {
    }

    public static Result calculate(List<Item> items, List<ScoringSection> scoring, Integer passScore) {
        List<SectionScore> sections = new ArrayList<>();
        double total = 0;
        int maxTotal = 0;
        boolean complete = true;
        boolean allAboveMin = true;

        for (ScoringSection section : scoring) {
            BigDecimal possible = BigDecimal.ZERO;
            BigDecimal earned = BigDecimal.ZERO;
            for (Item item : items) {
                if (section.sections().contains(item.section())) {
                    possible = possible.add(item.points());
                    if (item.correct()) {
                        earned = earned.add(item.points());
                    }
                }
            }
            if (possible.signum() == 0) {
                complete = false;
                continue;
            }
            double score = earned.multiply(BigDecimal.valueOf(section.maxScore()))
                    .divide(possible, 2, RoundingMode.HALF_UP).doubleValue();
            boolean below = score < section.minScore();
            allAboveMin &= !below;
            sections.add(new SectionScore(section.code(), score, section.maxScore(), section.minScore(), below));
            total += score;
            maxTotal += section.maxScore();
        }

        total = BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP).doubleValue();
        Boolean passed = complete && passScore != null ? (total >= passScore && allAboveMin) : null;
        return new Result(total, maxTotal, passScore, passed, complete, sections);
    }
}
