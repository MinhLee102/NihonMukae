package com.nihonmukae.backend.dictionary.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class ExampleSentenceResponse {
    private String sentence;
    private String translation;
}
