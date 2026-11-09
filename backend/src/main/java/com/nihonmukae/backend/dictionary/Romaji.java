package com.nihonmukae.backend.dictionary;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

/** Đổi romaji (Hepburn, có nhận cả kiểu Kunrei như si, ti, tu) sang hiragana, và bỏ dấu tiếng Việt. */
final class Romaji {

    private static final Map<String, String> TABLE = new HashMap<>();
    private static final String VOWELS = "aiueo";

    static {
        String[] rows = {
                "a あ", "i い", "u う", "e え", "o お",
                "ka か", "ki き", "ku く", "ke け", "ko こ",
                "ga が", "gi ぎ", "gu ぐ", "ge げ", "go ご",
                "sa さ", "shi し", "si し", "su す", "se せ", "so そ",
                "za ざ", "ji じ", "zi じ", "zu ず", "ze ぜ", "zo ぞ",
                "ta た", "chi ち", "ti ち", "tsu つ", "tu つ", "te て", "to と",
                "da だ", "di ぢ", "du づ", "de で", "do ど",
                "na な", "ni に", "nu ぬ", "ne ね", "no の",
                "ha は", "hi ひ", "fu ふ", "hu ふ", "he へ", "ho ほ",
                "ba ば", "bi び", "bu ぶ", "be べ", "bo ぼ",
                "pa ぱ", "pi ぴ", "pu ぷ", "pe ぺ", "po ぽ",
                "ma ま", "mi み", "mu む", "me め", "mo も",
                "ya や", "yu ゆ", "yo よ",
                "ra ら", "ri り", "ru る", "re れ", "ro ろ",
                "wa わ", "wo を", "wi うぃ", "we うぇ",
                "kya きゃ", "kyu きゅ", "kyo きょ", "gya ぎゃ", "gyu ぎゅ", "gyo ぎょ",
                "sha しゃ", "shu しゅ", "sho しょ", "sya しゃ", "syu しゅ", "syo しょ",
                "ja じゃ", "ju じゅ", "jo じょ", "jya じゃ", "jyu じゅ", "jyo じょ",
                "zya じゃ", "zyu じゅ", "zyo じょ",
                "cha ちゃ", "chu ちゅ", "cho ちょ", "tya ちゃ", "tyu ちゅ", "tyo ちょ",
                "nya にゃ", "nyu にゅ", "nyo にょ", "hya ひゃ", "hyu ひゅ", "hyo ひょ",
                "bya びゃ", "byu びゅ", "byo びょ", "pya ぴゃ", "pyu ぴゅ", "pyo ぴょ",
                "mya みゃ", "myu みゅ", "myo みょ", "rya りゃ", "ryu りゅ", "ryo りょ",
                "fa ふぁ", "fi ふぃ", "fe ふぇ", "fo ふぉ", "ti てぃ", "di でぃ", "tsa つぁ",
                "-", "ー"
        };
        for (String row : rows) {
            String[] kv = row.split(" ");
            if (kv.length == 2) {
                TABLE.put(kv[0], kv[1]);
            }
        }
        TABLE.put("-", "ー");
        // ti, di thông thường là ち, ぢ: các dạng てぃ, でぃ ít gặp nên bỏ
        TABLE.put("ti", "ち");
        TABLE.put("di", "ぢ");
    }

    private Romaji() {
    }

    /** Hiragana, hoặc null nếu chuỗi không phải romaji hợp lệ (còn chữ không đổi được, có khoảng trắng...). */
    static String toHiragana(String input) {
        if (input == null) {
            return null;
        }
        String s = expandMacrons(input.trim().toLowerCase());
        if (s.length() < 2 || !s.matches("[a-z'\\-]+")) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            char next = i + 1 < s.length() ? s.charAt(i + 1) : 0;
            if (c == '\'') {
                i++;
                continue;
            }
            if (c == 'n' && (next == 0 || (VOWELS.indexOf(next) < 0 && next != 'y'))) {
                out.append('ん');
                i += next == '\'' ? 2 : 1;
                continue;
            }
            // Phụ âm đôi (kk, ss, tt, pp...) hoặc tch (matcha) → っ, trừ nn đã xử lý ở trên
            if ((c == next && VOWELS.indexOf(c) < 0 && c != 'n') || (c == 't' && next == 'c')) {
                out.append('っ');
                i++;
                continue;
            }
            String kana = null;
            int len = 0;
            for (int l = Math.min(3, s.length() - i); l >= 1; l--) {
                String hit = TABLE.get(s.substring(i, i + l));
                if (hit != null) {
                    kana = hit;
                    len = l;
                    break;
                }
            }
            if (kana == null) {
                return null;
            }
            out.append(kana);
            i += len;
        }
        return out.toString();
    }

    private static String expandMacrons(String s) {
        return s.replace("ā", "aa").replace("ī", "ii").replace("ū", "uu").replace("ē", "ee").replace("ō", "ou");
    }

    /** Bỏ dấu tiếng Việt (kể cả đ → d). Chuỗi không có dấu thì giữ nguyên. */
    static String stripAccents(String s) {
        String d = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return d.replace('đ', 'd').replace('Đ', 'D');
    }
}
