package com.nihonmukae.backend.grammar;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Kiểm tra nội dung ngữ pháp AI viết cho một mẫu: lỗi thì bỏ mục (mẫu đó vào lại batch sau), cảnh báo thì vẫn
 * nhận. Thay cho scripts/grammar/check.py cũ.
 */
public final class GrammarValidator {

    private GrammarValidator() {
    }

    private static final Pattern VI_LETTER = Pattern.compile(
            "[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]", Pattern.CASE_INSENSITIVE);
    private static final Pattern VI_WORD = Pattern.compile(
            "(?<![\\p{L}])(là|và|của|không|được|một|những|khi|để|cho|với|có|người|này|trong|thì|nên|phải|rất)(?![\\p{L}])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern JA_CHAR = Pattern.compile("[\\u3040-\\u30FF\\u4E00-\\u9FFF]");
    private static final Pattern FURIGANA = Pattern.compile("[\\u4E00-\\u9FFF]+[（(][\\u3040-\\u309F]+[）)]");
    // Phần ký hiệu của tên mẫu (〜, V, N, dấu ngoặc...): bỏ đi để lấy đoạn chữ Nhật đặc trưng của mẫu
    private static final Pattern PLACEHOLDER = Pattern.compile("[A-Za-zＡ-Ｚ0-9〜～~…\\s/／・(),（）、。]+");
    static final int MAX_MEANING = 250;
    static final int MAX_EXPLANATION = 1200;
    static final int MAX_NOTES = 800;

    public record Example(String ja, String vi) {
    }

    public record Similar(String pattern, String diff) {
    }

    /** Nội dung đã làm sạch, sẵn sàng lưu. */
    public record Content(String reading, List<String> connection, String meaning, String explanation, String notes,
                          List<Similar> similar, List<Example> examples) {
    }

    /** content null khi có lỗi. */
    public record Result(Content content, List<String> errors, List<String> warnings) {
    }

    static boolean isVietnamese(String text) {
        return VI_LETTER.matcher(text).find() || VI_WORD.matcher(text).find();
    }

    /** Khoá so trùng tên mẫu: NFKC, ~ thành 〜, bỏ khoảng trắng. */
    public static String normPattern(String p) {
        String s = Normalizer.normalize(p == null ? "" : p, Normalizer.Form.NFKC).replace('~', '〜').replace('～', '〜');
        return s.replaceAll("\\s+", "");
    }

    @SuppressWarnings("unchecked")
    public static Result validate(Map<String, Object> entry, String pattern, String fallbackReading) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Map<String, String> texts = new LinkedHashMap<>();
        for (String key : List.of("meaning", "explanation")) {
            Object v = entry.get(key);
            int max = key.equals("meaning") ? MAX_MEANING : MAX_EXPLANATION;
            if (!(v instanceof String s) || s.isBlank()) {
                errors.add("thiếu " + key);
            } else if (!isVietnamese(s)) {
                errors.add(key + " không phải tiếng Việt");
            } else if (s.length() > max) {
                errors.add(key + " quá dài (" + s.length() + " ký tự)");
            } else {
                texts.put(key, s.trim());
            }
        }

        Object notesRaw = entry.get("notes");
        String notes = "";
        if (notesRaw != null) {
            if (!(notesRaw instanceof String s) || s.length() > MAX_NOTES) {
                errors.add("notes không hợp lệ hoặc quá dài");
            } else {
                notes = s.trim();
            }
        }

        List<String> connection = new ArrayList<>();
        Object conn = entry.get("connection");
        if (conn instanceof List<?> list && !list.isEmpty()
                && list.stream().allMatch(c -> c instanceof String s && !s.isBlank())) {
            list.forEach(c -> connection.add(((String) c).trim()));
        } else {
            errors.add("connection phải là mảng chuỗi, không rỗng");
        }

        List<Similar> similar = new ArrayList<>();
        Object sim = entry.get("similar");
        if (sim != null) {
            boolean ok = sim instanceof List<?> list && list.stream().allMatch(s -> s instanceof Map<?, ?> m
                    && m.get("pattern") instanceof String p && !p.isBlank()
                    && m.get("diff") instanceof String d && !d.isBlank());
            if (!ok) {
                errors.add("similar sai định dạng (cần mảng {pattern, diff})");
            } else {
                for (Object o : (List<?>) sim) {
                    Map<String, Object> m = (Map<String, Object>) o;
                    if (similar.size() < 2) {
                        similar.add(new Similar(((String) m.get("pattern")).trim(), ((String) m.get("diff")).trim()));
                    }
                }
            }
        }

        List<Example> examples = new ArrayList<>();
        Object ex = entry.get("examples");
        if (!(ex instanceof List<?> list)) {
            errors.add("thiếu examples");
        } else {
            int i = 0;
            for (Object o : list) {
                i++;
                String ja = o instanceof Map<?, ?> m && m.get("ja") instanceof String s ? s.trim() : "";
                String vi = o instanceof Map<?, ?> m && m.get("vi") instanceof String s ? s.trim() : "";
                if (ja.isEmpty() || !JA_CHAR.matcher(ja).find() || vi.isEmpty()) {
                    errors.add("ví dụ " + i + " thiếu câu Nhật hoặc bản dịch");
                    continue;
                }
                if (!isVietnamese(vi)) {
                    errors.add("ví dụ " + i + ": bản dịch không phải tiếng Việt");
                    continue;
                }
                if (FURIGANA.matcher(ja).find()) {
                    warnings.add("ví dụ " + i + " có furigana trong ngoặc");
                }
                examples.add(new Example(ja, vi));
            }
            if (examples.size() < 2) {
                errors.add("chỉ có " + examples.size() + " ví dụ hợp lệ (cần ít nhất 2)");
            } else if (examples.size() < 3) {
                warnings.add("chỉ có " + examples.size() + " ví dụ");
            }
            String probe = Arrays.stream(PLACEHOLDER.split(pattern)).filter(c -> c.length() >= 2)
                    .max(Comparator.comparingInt(String::length)).map(c -> c.substring(0, 2)).orElse(null);
            if (probe != null && !examples.isEmpty()) {
                List<Integer> missing = new ArrayList<>();
                for (int k = 0; k < examples.size(); k++) {
                    if (!examples.get(k).ja().contains(probe)) {
                        missing.add(k + 1);
                    }
                }
                if (!missing.isEmpty()) {
                    warnings.add("ví dụ " + missing + " không thấy chứa \"" + probe + "…\" của mẫu");
                }
            }
        }

        if (!errors.isEmpty()) {
            return new Result(null, errors, warnings);
        }
        Object r = entry.get("reading");
        String reading = r instanceof String s && !s.isBlank() ? s.trim() : fallbackReading;
        return new Result(new Content(reading, connection, texts.get("meaning"), texts.get("explanation"), notes,
                similar, examples.subList(0, Math.min(3, examples.size()))), errors, warnings);
    }
}
