package com.nihonmukae.backend.question.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * File JSON import đề và câu hỏi (format trong spec 5.8.1). Câu đơn nằm trong
 * "questions", đoạn văn hoặc audio dùng chung nằm trong "groups" kèm các câu con.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImportFile {

    private TestInfo test;
    private List<Item> questions = new ArrayList<>();
    private List<Group> groups = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TestInfo {
        @JsonDeserialize(using = LevelDeserializer.class)
        private Integer level;
        private Integer year;
        private Integer month;
        private String title;
        @JsonAlias("source_note")
        private String sourceNote;
        // Audio của cả phần nghe (một file, phát liền từ đầu đến cuối khi thi thử)
        @JsonAlias("audio_url")
        private String audioUrl;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        // Mã dạng bài (question_types.code). Câu con của group có thể bỏ trống để kế thừa từ group
        private String type;
        @JsonDeserialize(using = LevelDeserializer.class)
        private Integer level;
        private String question;
        private List<String> options;
        private String correct;
        private String explanation;
        @JsonAlias("audio_url")
        private String audioUrl;
        // Bản chữ của câu nghe (tùy chọn), hiện sau khi nộp bài
        private String transcript;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Group {
        private String type;
        @JsonDeserialize(using = LevelDeserializer.class)
        private Integer level;
        private String title;
        private String topic;
        private String passage;
        @JsonAlias("audio_url")
        private String audioUrl;
        @JsonAlias("image_url")
        private String imageUrl;
        private List<Item> questions = new ArrayList<>();
    }
}
