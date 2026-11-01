package com.nihonmukae.backend.flashcard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardResponse {
    private UUID id;
    private UUID deckId;
    private String front;
    private String back;
    private String imageUrl;
    private Long wordId;
    private ZonedDateTime createdAt;
}
