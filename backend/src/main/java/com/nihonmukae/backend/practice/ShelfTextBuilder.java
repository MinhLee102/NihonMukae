package com.nihonmukae.backend.practice;

import com.nihonmukae.backend.practice.dto.ResultDtos.Result;
import com.nihonmukae.backend.practice.dto.ResultDtos.ResultGroup;
import com.nihonmukae.backend.practice.dto.ResultDtos.ResultQuestion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dựng văn bản tiếng Nhật (không có tên dạng bài vì tên là tiếng Việt) để lưu vào My Shelf từ snapshot kết quả. Không đưa phần giải thích vào vì nó là
 * tiếng Việt, không có furigana; người học vẫn xem được giải thích ở trang kết quả.
 */
public final class ShelfTextBuilder {

    private static final String[] CIRCLED = {"①", "②", "③", "④", "⑤", "⑥"};

    private ShelfTextBuilder() {
    }

    /** Một mục My Shelf sẽ tạo: nhãn phần (tiếng Việt, dùng cho tiêu đề) và văn bản. */
    public record Part(String label, String text) {
    }

    /**
     * Chia các câu thành nhiều mục để mục nào cũng không quá maxLength: trước hết theo phần (Ngôn ngữ, Đọc hiểu,
     * Nghe hiểu); phần nào vẫn dài thì gom theo từng 問題, một 問題 quá dài thì gom theo từng câu.
     * Ít câu (vừa một mục) thì trả về đúng một mục không nhãn, giữ cách lưu cũ.
     */
    public static List<Part> split(Result result, List<Integer> positions, int maxLength) {
        String whole = build(result, positions);
        if (whole.length() <= maxLength) {
            return List.of(new Part(null, whole));
        }
        Map<String, List<ResultQuestion>> byPart = new LinkedHashMap<>();
        for (ResultQuestion q : result.questions()) {
            if (positions.contains(q.position())) {
                byPart.computeIfAbsent(partLabel(q.section()), k -> new ArrayList<>()).add(q);
            }
        }
        List<Part> parts = new ArrayList<>();
        for (Map.Entry<String, List<ResultQuestion>> entry : byPart.entrySet()) {
            List<List<Integer>> chunks = pack(result, mondaiUnits(entry.getValue()), maxLength);
            for (int i = 0; i < chunks.size(); i++) {
                String label = chunks.size() == 1 ? entry.getKey() : entry.getKey() + " " + (i + 1);
                parts.add(new Part(label, build(result, chunks.get(i))));
            }
        }
        return parts;
    }

    private static String partLabel(String section) {
        if ("dokkai".equals(section)) {
            return "Đọc hiểu";
        }
        if ("chokai".equals(section)) {
            return "Nghe hiểu";
        }
        return "Ngôn ngữ";
    }

    /** Các câu liền nhau cùng một 問題 thành một khối. */
    private static List<List<Integer>> mondaiUnits(List<ResultQuestion> questions) {
        List<List<Integer>> units = new ArrayList<>();
        Integer lastMondai = null;
        for (ResultQuestion q : questions) {
            if (units.isEmpty() || q.mondaiNo() == null || !q.mondaiNo().equals(lastMondai)) {
                units.add(new ArrayList<>());
            }
            units.get(units.size() - 1).add(q.position());
            lastMondai = q.mondaiNo();
        }
        return units;
    }

    /** Gom khối vào mục cho tới khi sắp vượt maxLength; một khối tự nó đã quá dài thì gom theo từng câu. */
    private static List<List<Integer>> pack(Result result, List<List<Integer>> units, int maxLength) {
        List<List<Integer>> chunks = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        for (List<Integer> unit : units) {
            List<List<Integer>> pieces = build(result, unit).length() <= maxLength
                    ? List.of(unit)
                    : unit.stream().map(List::of).toList();
            for (List<Integer> piece : pieces) {
                List<Integer> candidate = new ArrayList<>(current);
                candidate.addAll(piece);
                if (!current.isEmpty() && build(result, candidate).length() > maxLength) {
                    chunks.add(current);
                    current = new ArrayList<>(piece);
                } else {
                    current = candidate;
                }
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current);
        }
        return chunks;
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
            if (q.transcript() != null && !q.transcript().isBlank()) {
                out.append("\nスクリプト：\n").append(plain(q.transcript()));
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
