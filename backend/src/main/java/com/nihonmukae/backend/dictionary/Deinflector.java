package com.nihonmukae.backend.dictionary;

import com.atilika.kuromoji.ipadic.Token;
import com.nihonmukae.backend.shared.nlp.KuromojiHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Đưa dạng chia của động từ và tính từ về dạng gốc để tra từ điển: 食べました → 食べる, 高かった → 高い.
 * Chỉ áp dụng khi cả chuỗi là một từ chia (từ gốc cộng các trợ động từ, trợ từ nối hoặc động từ phụ như ている),
 * để 食べ物 hay 暗号化 không bị tách bậy.
 */
@Component
public class Deinflector {

    private static final Pattern JAPANESE = Pattern.compile("[぀-ヿ㐀-鿿]");
    private static final int MAX_LENGTH = 30;
    private static final Set<String> TAIL = Set.of("助動詞", "助詞");

    /** Dạng gốc của từ chia, rỗng khi chuỗi không phải một từ chia hoặc đã là dạng gốc. */
    public List<String> baseForms(String query) {
        if (query == null || query.length() > MAX_LENGTH || !JAPANESE.matcher(query).find()) {
            return List.of();
        }
        List<Token> tokens = KuromojiHolder.get().tokenize(query);
        if (tokens.isEmpty()) {
            return List.of();
        }
        Token head = tokens.get(0);
        String pos = head.getPartOfSpeechLevel1();
        if (!"動詞".equals(pos) && !"形容詞".equals(pos)) {
            return List.of();
        }
        for (Token t : tokens.subList(1, tokens.size())) {
            String p1 = t.getPartOfSpeechLevel1();
            String p2 = t.getPartOfSpeechLevel2();
            boolean auxiliaryVerb = "動詞".equals(p1) && ("非自立".equals(p2) || "接尾".equals(p2));
            if (!TAIL.contains(p1) && !auxiliaryVerb) {
                return List.of();
            }
        }
        String base = head.getBaseForm();
        if (base == null || "*".equals(base) || base.equals(query)) {
            return List.of();
        }
        return List.of(base);
    }
}
