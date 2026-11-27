package com.nihonmukae.backend.grammar;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dò mẫu ngữ pháp trùng nhau giữa các cấp (ví dụ 〜終わる ở cả N4 và N3): cùng tên sau khi chuẩn hoá, hoặc cùng cách
 * đọc (nên 〜終わる và 〜おわる vẫn khớp). Chỉ là gợi ý: cùng tên mà khác nghĩa (〜ように ở N4 và N3) thì admin giữ riêng.
 */
public final class GrammarDuplicates {

    private GrammarDuplicates() {
    }

    public record Point(long id, int level, String pattern, String reading) {
    }

    /** Tên mẫu bỏ 〜, dấu ba chấm và khoảng trắng. */
    static String patternKey(String pattern) {
        String s = GrammarValidator.normPattern(pattern);
        return s.replace("〜", "").replace("…", "").replace("...", "");
    }

    /** Cách đọc đưa về hiragana, bỏ 〜 và khoảng trắng; null khi quá ngắn để so (1 ký tự dễ khớp nhầm). */
    static String readingKey(String reading) {
        if (reading == null || reading.isBlank()) {
            return null;
        }
        String s = patternKey(Normalizer.normalize(reading, Normalizer.Form.NFKC));
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append(c >= 0x30A1 && c <= 0x30F6 ? (char) (c - 0x60) : c);
        }
        return sb.length() >= 2 ? sb.toString() : null;
    }

    static long pairKey(long a, long b) {
        long lo = Math.min(a, b);
        long hi = Math.max(a, b);
        return lo * 1_000_000_007L + hi;
    }

    /**
     * Các nhóm mẫu trùng có ít nhất hai cấp khác nhau. distinctPairs: cặp đã được admin giữ riêng (pairKey).
     * Mỗi nhóm xếp theo cấp từ thấp đến cao (N5 trước).
     */
    public static List<List<Point>> groups(List<Point> points, Set<Long> distinctPairs) {
        Map<Long, Long> parent = new HashMap<>();
        Map<Long, Point> byId = new LinkedHashMap<>();
        for (Point p : points) {
            parent.put(p.id(), p.id());
            byId.put(p.id(), p);
        }
        Map<String, List<Point>> buckets = new HashMap<>();
        for (Point p : points) {
            buckets.computeIfAbsent("p:" + patternKey(p.pattern()), k -> new ArrayList<>()).add(p);
            String r = readingKey(p.reading());
            if (r != null) {
                buckets.computeIfAbsent("r:" + r, k -> new ArrayList<>()).add(p);
            }
        }
        for (List<Point> bucket : buckets.values()) {
            for (int i = 0; i < bucket.size(); i++) {
                for (int j = i + 1; j < bucket.size(); j++) {
                    Point a = bucket.get(i);
                    Point b = bucket.get(j);
                    if (a.level() != b.level() && !distinctPairs.contains(pairKey(a.id(), b.id()))) {
                        union(parent, a.id(), b.id());
                    }
                }
            }
        }
        Map<Long, List<Point>> grouped = new LinkedHashMap<>();
        for (Point p : byId.values()) {
            grouped.computeIfAbsent(find(parent, p.id()), k -> new ArrayList<>()).add(p);
        }
        List<List<Point>> out = new ArrayList<>();
        for (List<Point> g : grouped.values()) {
            if (g.size() >= 2 && g.stream().map(Point::level).distinct().count() >= 2) {
                g.sort(Comparator.comparingInt(Point::level).reversed().thenComparingLong(Point::id));
                out.add(g);
            }
        }
        out.sort(Comparator.comparingInt((List<Point> g) -> -g.get(0).level()).thenComparing(g -> g.get(0).pattern()));
        return out;
    }

    private static long find(Map<Long, Long> parent, long x) {
        long root = x;
        while (parent.get(root) != root) {
            root = parent.get(root);
        }
        while (parent.get(x) != root) {
            long next = parent.get(x);
            parent.put(x, root);
            x = next;
        }
        return root;
    }

    private static void union(Map<Long, Long> parent, long a, long b) {
        long ra = find(parent, a);
        long rb = find(parent, b);
        if (ra != rb) {
            parent.put(ra, rb);
        }
    }
}
