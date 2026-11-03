package com.nihonmukae.backend.question;

import com.nihonmukae.backend.shared.entity.JlptTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Khóa nhận diện câu và nhóm khi import. Một câu được coi là "đã có" chỉ khi cùng đề (hoặc cùng kho luyện tập
 * khi file không có đề), cùng nội dung và cùng thứ tự xuất hiện trong file. Thứ tự xuất hiện là để các câu
 * giống hệt nhau trong một đề (câu nghe cùng "質問を聞いてください" và đáp án 1 2 3 4) vẫn là các câu riêng,
 * và import lại cùng một file cho ra đúng các khóa cũ nên không tạo bản sao.
 * Audio, ảnh, đáp án đúng và giải thích là dữ liệu của câu, không dùng để nhận diện.
 */
final class ImportKeys {

    private final String scope;
    private final Map<String, Integer> occurrences = new HashMap<>();

    ImportKeys(String scope) {
        this.scope = scope;
    }

    static String scopeOf(JlptTest test) {
        return test == null ? "bank" : "t" + test.getId();
    }

    String question(String typeCode, int level, String question, List<String> options, String passage) {
        return next(ContentHasher.hashQuestion(typeCode, level, question, options, passage));
    }

    String group(String typeCode, int level, String passage) {
        return next("g|" + typeCode + '|' + level + '|' + ContentHasher.normalize(passage));
    }

    private String next(String base) {
        int n = occurrences.merge(base, 1, Integer::sum);
        return ContentHasher.sha256(scope + '|' + base + '|' + n);
    }
}
