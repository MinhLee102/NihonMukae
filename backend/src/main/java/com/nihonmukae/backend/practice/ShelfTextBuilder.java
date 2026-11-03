package com.nihonmukae.backend.practice;

import com.nihonmukae.backend.practice.dto.ResultDtos.Result;
import com.nihonmukae.backend.practice.dto.ResultDtos.ResultGroup;
import com.nihonmukae.backend.practice.dto.ResultDtos.ResultQuestion;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dựng văn bản tiếng Nhật (không có tên dạng bài vì tên là tiếng Việt) để lưu vào My Shelf từ snapshot kết quả. Không đưa phần giải thích vào vì nó là
 * tiếng Việt, không có furigana; người học vẫn xem được giải thích ở trang kết quả.
 */
public final class ShelfTextBuilder {

    private static final String[] CIRCLED = {"①", "②", "③", "④", "⑤", "⑥"};

    private ShelfTextBuilder() {
    }

    /** @param positions các câu cần lưu, theo thứ tự trong bài */
    public static String build(Result result, List<Integer> positions) {
        StringBuilder out = new StringBuilder();
        Set<Long> shownGroups = new HashSet<>();
        for (ResultQuestion q : result.questions()) {
            if (!positions.contains(q.position())) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append("\n\n");
            }
            out.append("【").append(q.position()).append("番】");
            out.append('\n');

            ResultGroup group = q.groupId() == null || result.groups() == null ? null
                    : result.groups().stream().filter(g -> q.groupId().equals(g.id())).findFirst().orElse(null);
            if (group != null && group.passage() != null && shownGroups.add(group.id())) {
                out.append(plain(group.passage())).append("\n\n");
            }

            String question = plain(q.question());
            if (!question.isBlank()) {
                out.append(question).append('\n');
            }
            List<String> options = q.options();
            for (int i = 0; i < options.size(); i++) {
                out.append(i < CIRCLED.length ? CIRCLED[i] : String.valueOf(i + 1)).append(' ')
                        .append(plain(options.get(i))).append('\n');
            }
            out.append("正解：").append(letterToNumber(q.correctAnswer()));
            if (q.selected() != null) {
                out.append("　選んだ答え：").append(letterToNumber(q.selected()));
            }
        }
        return out.toString().trim();
    }

    private static String letterToNumber(String letter) {
        return letter == null || letter.isEmpty() ? "?" : String.valueOf(letter.charAt(0) - 'A' + 1);
    }

    /** Bỏ markup của kho câu hỏi (gạch chân, đậm, ảnh, khung, căn lề) để còn lại chữ thường. */
    static String plain(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("\\[\\[img:[^\\]]+\\]\\]", "（図）")
                .replaceAll("</?u>", "")
                .replaceAll("\\*\\*", "")
                .replaceAll("\\[\\[/?(box|center|right)\\]\\]", "")
                .replaceAll("(?m)^\\s*\\|[-: |]+\\|\\s*$\\n?", "")
                .replaceAll("(?m)^\\s*\\|\\s*|\\s*\\|\\s*$", "")
                .replaceAll("\\s*\\|\\s*", "　")
                .trim();
    }
}
