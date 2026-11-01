package com.nihonmukae.backend.question;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nihonmukae.backend.question.dto.ImportFile;
import com.nihonmukae.backend.shared.entity.QuestionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, QuestionType> types = Map.of(
            "kanji_reading", type("kanji_reading", false, 5, 4, 3),
            "reading_short", type("reading_short", true, 5, 4, 3));

    private static QuestionType type(String code, boolean group, int... levels) {
        List<Short> l = new java.util.ArrayList<>();
        for (int level : levels) {
            l.add((short) level);
        }
        return QuestionType.builder().id((short) 1).code(code).levels(l).groupBased(group).build();
    }

    private List<ImportValidator.Problem> validate(String json) throws Exception {
        return ImportValidator.validate(mapper.readValue(json, ImportFile.class), types);
    }

    @Test
    void validFileHasNoProblems() throws Exception {
        String json = """
                {"test":{"level":3,"year":2023,"month":7},
                 "questions":[{"type":"kanji_reading","question":"漢字","options":["a","b","c","d"],"correct":"B"}],
                 "groups":[{"type":"reading_short","passage":"本文","questions":[
                    {"question":"問い","options":["a","b","c"],"correct":"C","audio_url":"http://x"}]}]}
                """;
        assertTrue(validate(json).isEmpty());
    }

    @Test
    void reportsPathForEachProblem() throws Exception {
        String json = """
                {"questions":[
                  {"type":"nope","level":3,"question":"q","options":["a","b"],"correct":"A"},
                  {"type":"kanji_reading","level":1,"question":"","options":["a","b","c","d"],"correct":"E"}]}
                """;
        List<ImportValidator.Problem> problems = validate(json);
        assertTrue(problems.stream().anyMatch(p -> p.path().equals("questions[0].type")));
        assertTrue(problems.stream().anyMatch(p -> p.path().equals("questions[0].options")));
        assertTrue(problems.stream().anyMatch(p -> p.path().equals("questions[1].level")));
        assertTrue(problems.stream().anyMatch(p -> p.path().equals("questions[1].question")));
        assertTrue(problems.stream().anyMatch(p -> p.path().equals("questions[1].correct")));
    }

    @Test
    void groupNeedsContentAndQuestions() throws Exception {
        List<ImportValidator.Problem> problems = validate("{\"groups\":[{\"type\":\"reading_short\",\"level\":3}]}");
        assertTrue(problems.stream().anyMatch(p -> p.path().equals("groups[0]")));
        assertTrue(problems.stream().anyMatch(p -> p.path().equals("groups[0].questions")));
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        assertEquals(1, validate("{}").size());
    }

    @Test
    void hashIgnoresWhitespaceAndFullWidthButNotContent() {
        String a = ContentHasher.hashQuestion("kanji_reading", 3, "漢字　の読み", List.of("a", "b"), null);
        String b = ContentHasher.hashQuestion("kanji_reading", 3, " 漢字 の読み ", List.of("a", "b"), null);
        String c = ContentHasher.hashQuestion("kanji_reading", 3, "漢字の読み", List.of("a", "c"), null);
        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(64, a.length());
    }
}
