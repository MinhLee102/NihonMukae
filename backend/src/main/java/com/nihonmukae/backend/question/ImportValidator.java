package com.nihonmukae.backend.question;

import com.nihonmukae.backend.question.dto.ImportFile;
import com.nihonmukae.backend.shared.entity.QuestionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Kiểm tra file import trước khi lưu. Thuần logic, không chạm DB, báo lỗi theo đường dẫn (vd groups[1].questions[0]). */
public final class ImportValidator {

    public record Problem(String path, String message) {
    }

    private static final int MAX_ITEMS = 1000;

    private ImportValidator() {
    }

    public static List<Problem> validate(ImportFile file, Map<String, QuestionType> typesByCode) {
        List<Problem> problems = new ArrayList<>();
        Integer testLevel = null;

        if (file.getTest() != null) {
            testLevel = file.getTest().getLevel();
            if (testLevel == null || testLevel < 1 || testLevel > 5) {
                problems.add(new Problem("test.level", "Level của đề phải từ 1 đến 5"));
                testLevel = null;
            }
            ImportFile.TestInfo t = file.getTest();
            boolean hasYearMonth = t.getYear() != null && t.getMonth() != null;
            boolean hasTitle = t.getTitle() != null && !t.getTitle().isBlank();
            if (!hasYearMonth && !hasTitle) {
                problems.add(new Problem("test", "Đề cần có year và month, hoặc có title"));
            }
            if (t.getMonth() != null && (t.getMonth() < 1 || t.getMonth() > 12)) {
                problems.add(new Problem("test.month", "Tháng phải từ 1 đến 12"));
            }
        }

        int total = file.getQuestions().size() + file.getGroups().stream().mapToInt(g -> g.getQuestions().size()).sum();
        if (total == 0) {
            problems.add(new Problem("", "File không có câu hỏi nào"));
        }
        if (total > MAX_ITEMS) {
            problems.add(new Problem("", "Mỗi lần import tối đa " + MAX_ITEMS + " câu"));
        }

        for (int i = 0; i < file.getQuestions().size(); i++) {
            checkItem(file.getQuestions().get(i), "questions[" + i + "]", null, null, testLevel, typesByCode, problems);
        }

        for (int g = 0; g < file.getGroups().size(); g++) {
            ImportFile.Group group = file.getGroups().get(g);
            String gPath = "groups[" + g + "]";
            boolean hasContent = notBlank(group.getPassage()) || notBlank(group.getAudioUrl()) || notBlank(group.getImageUrl());
            if (!hasContent) {
                problems.add(new Problem(gPath, "Nhóm cần có passage, audio_url hoặc image_url"));
            }
            if (group.getQuestions().isEmpty()) {
                problems.add(new Problem(gPath + ".questions", "Nhóm cần có ít nhất 1 câu hỏi"));
            }
            Integer groupLevel = group.getLevel() != null ? group.getLevel() : testLevel;
            if (group.getType() != null && !typesByCode.containsKey(group.getType())) {
                problems.add(new Problem(gPath + ".type", "Dạng bài không tồn tại: " + group.getType()));
            }
            for (int i = 0; i < group.getQuestions().size(); i++) {
                checkItem(group.getQuestions().get(i), gPath + ".questions[" + i + "]", group.getType(), groupLevel,
                        testLevel, typesByCode, problems);
            }
        }
        return problems;
    }

    private static void checkItem(ImportFile.Item item, String path, String inheritedType, Integer inheritedLevel,
                                  Integer testLevel, Map<String, QuestionType> typesByCode, List<Problem> problems) {
        String typeCode = notBlank(item.getType()) ? item.getType() : inheritedType;
        QuestionType type = typeCode == null ? null : typesByCode.get(typeCode);
        if (typeCode == null) {
            problems.add(new Problem(path + ".type", "Thiếu dạng bài (type)"));
        } else if (type == null) {
            problems.add(new Problem(path + ".type", "Dạng bài không tồn tại: " + typeCode));
        }

        Integer level = item.getLevel() != null ? item.getLevel() : inheritedLevel != null ? inheritedLevel : testLevel;
        if (level == null) {
            problems.add(new Problem(path + ".level", "Thiếu level"));
        } else if (level < 1 || level > 5) {
            problems.add(new Problem(path + ".level", "Level phải từ 1 đến 5"));
        } else if (type != null && !type.getLevels().contains(level.shortValue())) {
            problems.add(new Problem(path + ".level", "Dạng bài " + typeCode + " không áp dụng cho N" + level));
        }

        if (!notBlank(item.getQuestion())) {
            problems.add(new Problem(path + ".question", "Thiếu nội dung câu hỏi"));
        }

        List<String> options = item.getOptions();
        if (options == null || options.size() < 3 || options.size() > 4) {
            problems.add(new Problem(path + ".options", "Cần 3 hoặc 4 đáp án"));
        } else {
            if (options.stream().anyMatch(o -> !notBlank(o))) {
                problems.add(new Problem(path + ".options", "Có đáp án bị để trống"));
            }
            String correct = item.getCorrect();
            if (correct == null || correct.length() != 1 || correct.charAt(0) < 'A'
                    || correct.charAt(0) >= 'A' + options.size()) {
                problems.add(new Problem(path + ".correct", "Đáp án đúng phải là một chữ cái từ A đến "
                        + (char) ('A' + options.size() - 1)));
            }
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
