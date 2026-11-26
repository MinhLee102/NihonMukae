package com.nihonmukae.backend.grammar;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrammarValidatorTest {

    private static Map<String, Object> good() {
        Map<String, Object> e = new HashMap<>();
        e.put("id", "G1");
        e.put("reading", "ざるをえない");
        e.put("connection", List.of("Vない(bỏ ない) + ざるを得ない"));
        e.put("meaning", "Đành phải..., buộc phải...");
        e.put("explanation", "Diễn tả việc không còn lựa chọn nào khác nên bắt buộc phải làm.");
        e.put("notes", "");
        e.put("similar", List.of(Map.of("pattern", "〜ないわけにはいかない", "diff", "Nhấn vào lý do khách quan.")));
        e.put("examples", List.of(
                Map.of("ja", "雨が強いので、試合を中止せざるを得ない。", "vi", "Vì mưa to nên đành phải hủy trận đấu."),
                Map.of("ja", "上司に頼まれたら、引き受けざるを得ません。", "vi", "Sếp đã nhờ thì không thể không nhận."),
                Map.of("ja", "証拠がある以上、事実を認めざるを得なかった。", "vi", "Đã có bằng chứng thì phải thừa nhận sự thật.")));
        return e;
    }

    @Test
    void acceptsAWellFormedEntry() {
        GrammarValidator.Result r = GrammarValidator.validate(good(), "〜ざるを得ない", null);
        assertNotNull(r.content(), r.errors().toString());
        assertEquals(3, r.content().examples().size());
        assertEquals(1, r.content().similar().size());
        assertTrue(r.warnings().isEmpty(), r.warnings().toString());
    }

    @Test
    void rejectsMissingOrNonVietnameseFields() {
        Map<String, Object> e = good();
        e.put("meaning", "Have no choice but to");
        e.remove("connection");
        GrammarValidator.Result r = GrammarValidator.validate(e, "〜ざるを得ない", null);
        assertNull(r.content());
        assertTrue(r.errors().stream().anyMatch(x -> x.contains("meaning")));
        assertTrue(r.errors().stream().anyMatch(x -> x.contains("connection")));
    }

    @Test
    void needsAtLeastTwoValidExamples() {
        Map<String, Object> e = good();
        e.put("examples", List.of(Map.of("ja", "雨が強いので、試合を中止せざるを得ない。", "vi", "Vì mưa to nên đành phải hủy.")));
        assertNull(GrammarValidator.validate(e, "〜ざるを得ない", null).content());
    }

    @Test
    void warnsWhenExamplesDoNotShowThePattern() {
        Map<String, Object> e = good();
        e.put("examples", List.of(
                Map.of("ja", "今日は暑いです。", "vi", "Hôm nay trời nóng."),
                Map.of("ja", "明日は雨です。", "vi", "Ngày mai trời mưa.")));
        GrammarValidator.Result r = GrammarValidator.validate(e, "〜ざるを得ない", null);
        assertNotNull(r.content());
        assertTrue(r.warnings().stream().anyMatch(x -> x.contains("không thấy chứa")));
    }

    @Test
    void normalisesPatternsForDuplicateCheck() {
        assertEquals(GrammarValidator.normPattern("〜ざるを得ない"), GrammarValidator.normPattern("~ ざるを得ない"));
        assertEquals(GrammarValidator.normPattern("〜ざるを得ない"), GrammarValidator.normPattern("～ざるを得ない"));
    }

    @Test
    void parsesBatchIds() {
        assertEquals(123L, AdminGrammarService.parseId("G123"));
        assertEquals(7L, AdminGrammarService.parseId("7"));
        assertNull(AdminGrammarService.parseId("N1-001"));
        assertNull(AdminGrammarService.parseId(""));
    }
}
