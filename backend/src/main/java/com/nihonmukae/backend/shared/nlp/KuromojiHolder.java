package com.nihonmukae.backend.shared.nlp;

import com.atilika.kuromoji.ipadic.Tokenizer;

/**
 * Một Tokenizer dùng chung cho cả furigana và tìm từ điển. Nạp từ điển ipadic khá nặng (vài chục MB),
 * nên không tạo nhiều bản; Tokenizer an toàn khi dùng từ nhiều luồng.
 */
public final class KuromojiHolder {

    private static final class Lazy {
        static final Tokenizer INSTANCE = new Tokenizer();
    }

    private KuromojiHolder() {
    }

    public static Tokenizer get() {
        return Lazy.INSTANCE;
    }
}
