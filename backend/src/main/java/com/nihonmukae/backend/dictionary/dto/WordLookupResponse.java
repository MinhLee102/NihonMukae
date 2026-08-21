package com.nihonmukae.backend.dictionary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class WordLookupResponse {
    private Long id;
    private String kanji;
    private String kana;
    private String hanViet;
    private String pronunciation;
    private String pos;
    private Short jlptLevel;
    private List<WordMeaningResponse> meanings;
    private List<ExampleSentenceResponse> examples; // giới hạn 2
}
