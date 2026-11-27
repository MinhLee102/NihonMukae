package com.nihonmukae.backend.grammar;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tìm các mẫu ngữ pháp xuất hiện trong một câu, bằng cách so chuỗi (không hiểu ngữ pháp thật nên vẫn có thể báo
 * nhầm, chỉ dùng để gợi ý).
 * <ul>
 *   <li>Tên mẫu tách theo 〜 thành các phần phải xuất hiện theo thứ tự (〜ば〜ほど: 読めば読むほど), mỗi khoảng trống
 *   giữa hai phần không quá {@link #MAX_GAP} ký tự. Dạng viết bằng cách đọc (にちがいない) cũng được thử.</li>
 *   <li>Phần cuối được chia: る, う, く... đổi sang dạng nối (〜ことにする khớp ことにした), い thành く, かっ
 *   (〜らしい khớp らしかった), ない thêm ませ (ざるを得ません), だ thì bỏ hoặc thành で, な.</li>
 *   <li>Phần đầu phải bắt đầu ở ranh giới từ (do bộ tách từ cho), để ても không khớp trong とても. Mẫu chỉ có hai
 *   chữ kana phải trùng trọn một hoặc vài từ. Mẫu một chữ (〜は, 〜に) bị bỏ qua vì gần như câu nào cũng có.</li>
 * </ul>
 */
public final class GrammarMatcher {

    static final int MAX_GAP = 12;

    public record Point(long id, String pattern, String reading) {
    }

    /** Đoạn [start, end) của câu khớp với mẫu id. */
    public record Hit(long id, int start, int end) {
    }

    /** Một cách viết của mẫu: các phần theo thứ tự; phần cuối có nhiều dạng chia. */
    private record Form(long id, List<String> head, List<String> lastVariants, boolean strict) {
    }

    private static final Map<Character, String[]> CONJ = Map.ofEntries(
            Map.entry('る', new String[]{"", "り", "っ", "ら", "れ"}),
            Map.entry('う', new String[]{"い", "っ", "わ", "え"}),
            Map.entry('く', new String[]{"き", "い", "か", "け"}),
            Map.entry('ぐ', new String[]{"ぎ", "い", "が", "げ"}),
            Map.entry('す', new String[]{"し", "さ", "せ"}),
            Map.entry('つ', new String[]{"ち", "っ", "た", "て"}),
            Map.entry('む', new String[]{"み", "ん", "ま", "め"}),
            Map.entry('ぶ', new String[]{"び", "ん", "ば", "べ"}),
            Map.entry('ぬ', new String[]{"に", "ん", "な", "ね"}),
            Map.entry('い', new String[]{"く", "かっ", "けれ", "さ"}),
            Map.entry('だ', new String[]{"", "で", "な", "じゃ", "だっ"}));

    private final List<Form> forms = new ArrayList<>();

    public GrammarMatcher(List<Point> points) {
        for (Point p : points) {
            Set<List<String>> seen = new LinkedHashSet<>();
            for (String source : new String[]{p.pattern(), p.reading()}) {
                if (source == null) {
                    continue;
                }
                for (String variant : source.split("[/／]")) {
                    List<String> pieces = pieces(variant);
                    if (!pieces.isEmpty() && seen.add(pieces)) {
                        Form f = form(p.id(), pieces);
                        if (f != null) {
                            forms.add(f);
                        }
                    }
                }
            }
        }
    }

    /** Các phần cố định của một cách viết: bỏ chú thích trong ngoặc, ký hiệu V, N, +, rồi tách theo 〜 và dấu ba chấm. */
    static List<String> pieces(String variant) {
        String s = Normalizer.normalize(variant, Normalizer.Form.NFKC)
                .replaceAll("\\([^)]*\\)", "")
                .replaceAll("[「」『』\\s]", "")
                .replaceAll("[A-Za-z]+[^+＋〜~]*[+＋]", "〜")
                .replaceAll("[A-Za-z+＋]+|\\.\\.\\.|…|・・・", "〜")
                .replace('~', '〜');
        List<String> out = new ArrayList<>();
        for (String piece : s.split("〜")) {
            if (!piece.isEmpty()) {
                out.add(piece);
            }
        }
        return out;
    }

    private static Form form(long id, List<String> pieces) {
        int total = pieces.stream().mapToInt(String::length).sum();
        if (total < 2) {
            return null;
        }
        boolean strict = total == 2 && pieces.stream().allMatch(GrammarMatcher::isKana);
        String last = pieces.get(pieces.size() - 1);
        List<String> variants = new ArrayList<>();
        variants.add(last);
        if (!strict && last.length() >= 2) {
            if (last.endsWith("する") || last.endsWith("くる")) {
                String base = last.substring(0, last.length() - 2);
                for (String e : last.endsWith("する") ? new String[]{"し", "さ", "せ"} : new String[]{"き", "こ"}) {
                    variants.add(base + e);
                }
            }
            String stem = last.substring(0, last.length() - 1);
            String[] endings = CONJ.get(last.charAt(last.length() - 1));
            if (endings != null) {
                for (String e : endings) {
                    String v = stem + e;
                    if (e.isEmpty() && total - 1 < 2) {
                        continue;
                    }
                    if (!v.isEmpty()) {
                        variants.add(v);
                    }
                }
            }
            if (last.endsWith("ない")) {
                variants.add(last.substring(0, last.length() - 2) + "ませ");
            }
            if (last.endsWith("ます")) {
                variants.add(last.substring(0, last.length() - 2) + "ませ");
                variants.add(last.substring(0, last.length() - 2) + "まし");
            }
        }
        // Dạng dài thử trước để đoạn khớp phủ được nhiều nhất
        variants.sort((a, b) -> b.length() - a.length());
        return new Form(id, pieces.subList(0, pieces.size() - 1), variants.stream().distinct().toList(), strict);
    }

    private static boolean isKana(String s) {
        return s.chars().allMatch(c -> (c >= 0x3041 && c <= 0x309F) || (c >= 0x30A0 && c <= 0x30FF));
    }

    /**
     * Các mẫu có trong text. starts: vị trí bắt đầu của từng từ do bộ tách từ cho (kèm text.length() làm ranh giới
     * cuối). Mỗi mẫu báo một lần (lần xuất hiện đầu, cách viết khớp dài nhất), bỏ đoạn khớp nằm gọn trong đoạn dài hơn của mẫu khác, xếp
     * theo vị trí.
     */
    public List<Hit> find(String text, Set<Integer> starts) {
        String t = Normalizer.normalize(text, Normalizer.Form.NFKC);
        if (t.length() != text.length()) {
            t = text; // NFKC đổi độ dài thì vị trí trả về sẽ lệch, nên so trên chuỗi gốc
        }
        java.util.Map<Long, Hit> best = new java.util.LinkedHashMap<>();
        for (Form f : forms) {
            Hit h = match(t, starts, f);
            Hit old = h == null ? null : best.get(f.id());
            // Nhiều cách viết cùng khớp (〜にあって / 〜にあっても) thì giữ đoạn dài nhất
            if (h != null && (old == null || h.end() - h.start() > old.end() - old.start())) {
                best.put(f.id(), h);
            }
        }
        List<Hit> all = List.copyOf(best.values());
        List<Hit> hits = new ArrayList<>(all);
        hits.removeIf(h -> all.stream().anyMatch(o -> o != h && o.start() <= h.start() && o.end() >= h.end()
                && (o.end() - o.start()) > (h.end() - h.start())));
        hits.sort((a, b) -> a.start() != b.start() ? a.start() - b.start() : b.end() - a.end());
        return hits;
    }

    private static Hit match(String t, Set<Integer> starts, Form f) {
        String first = f.head().isEmpty() ? null : f.head().get(0);
        for (int i = 0; i < t.length(); i++) {
            if (!starts.contains(i)) {
                continue;
            }
            int pos = i;
            boolean ok = true;
            for (int k = 0; k < f.head().size(); k++) {
                String piece = f.head().get(k);
                int at = k == 0 ? (t.startsWith(piece, i) ? i : -1) : t.indexOf(piece, pos);
                if (at < 0 || at - pos > MAX_GAP) {
                    ok = false;
                    break;
                }
                pos = at + piece.length();
            }
            if (!ok) {
                continue;
            }
            for (String last : f.lastVariants()) {
                int at = first == null ? (t.startsWith(last, i) ? i : -1) : t.indexOf(last, pos);
                if (at < 0 || at - pos > MAX_GAP) {
                    continue;
                }
                int end = at + last.length();
                if (f.strict() && !starts.contains(end)) {
                    continue;
                }
                return new Hit(f.id(), i, end);
            }
        }
        return null;
    }
}
