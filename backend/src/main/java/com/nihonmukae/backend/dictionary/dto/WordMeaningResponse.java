package com.nihonmukae.backend.dictionary.dto;

import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WordMeaningResponse {
    private String lang;
    private String pos;
    private String meaning;
}


