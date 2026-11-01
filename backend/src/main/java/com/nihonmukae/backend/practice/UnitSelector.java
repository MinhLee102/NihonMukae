package com.nihonmukae.backend.practice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Chọn "đơn vị" câu hỏi để đạt số câu mục tiêu. Câu đứng riêng là đơn vị 1 câu; đoạn văn hoặc audio
 * dùng chung phải bốc cả nhóm (câu hỏi vô nghĩa nếu thiếu đoạn văn), nên đơn vị nhóm có nhiều câu.
 */
public final class UnitSelector {

    public record Unit(Long groupId, List<Long> questionIds) {
        public int size() {
            return questionIds.size();
        }
    }

    private UnitSelector() {
    }

    /**
     * Bốc ngẫu nhiên cho tới khi đủ target câu. Có thể lệch tối đa (kích thước nhóm - 1) câu khi
     * bốc nhóm; ưu tiên nhóm vừa khít phần còn thiếu để lệch ít nhất. Không đủ câu thì lấy hết.
     */
    public static List<Unit> select(List<Unit> pool, int target, Random random) {
        List<Unit> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, random);

        List<Unit> chosen = new ArrayList<>();
        int total = 0;
        for (Unit unit : shuffled) {
            if (total >= target) {
                break;
            }
            if (total + unit.size() <= target) {
                chosen.add(unit);
                total += unit.size();
            }
        }
        // Còn thiếu mà các đơn vị còn lại đều quá lớn: lấy thêm đơn vị nhỏ nhất còn lại (lệch ít nhất có thể)
        if (total < target) {
            shuffled.removeAll(chosen);
            shuffled.sort((a, b) -> Integer.compare(a.size(), b.size()));
            if (!shuffled.isEmpty()) {
                Unit extra = shuffled.get(0);
                chosen.add(extra);
            }
        }
        return chosen;
    }

    public static int countQuestions(List<Unit> units) {
        return units.stream().mapToInt(Unit::size).sum();
    }
}
