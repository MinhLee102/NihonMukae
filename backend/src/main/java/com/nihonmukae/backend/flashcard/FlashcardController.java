package com.nihonmukae.backend.flashcard;

import com.nihonmukae.backend.auth.jwt.JwtUserPrincipal;
import com.nihonmukae.backend.flashcard.dto.CardRequest;
import com.nihonmukae.backend.flashcard.dto.CardResponse;
import com.nihonmukae.backend.flashcard.dto.DeckRequest;
import com.nihonmukae.backend.flashcard.dto.DeckResponse;
import com.nihonmukae.backend.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Mọi endpoint dưới /flashcard đều yêu cầu JWT (SecurityConfig: anyRequest authenticated)
@RestController
@RequestMapping("/flashcard")
public class FlashcardController {

    private final FlashcardService flashcardService;

    public FlashcardController(FlashcardService flashcardService) {
        this.flashcardService = flashcardService;
    }

    @GetMapping("/decks")
    public ApiResponse<List<DeckResponse>> listDecks(@AuthenticationPrincipal JwtUserPrincipal principal) {
        return ApiResponse.success(flashcardService.listDecks(principal.getId()));
    }

    @PostMapping("/decks")
    public ApiResponse<DeckResponse> createDeck(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                @Valid @RequestBody DeckRequest req) {
        return ApiResponse.success(flashcardService.createDeck(principal.getId(), req));
    }

    @PutMapping("/decks/{id}")
    public ApiResponse<DeckResponse> updateDeck(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                @PathVariable UUID id,
                                                @Valid @RequestBody DeckRequest req) {
        return ApiResponse.success(flashcardService.updateDeck(principal.getId(), id, req));
    }

    @GetMapping("/decks/{id}/cards")
    public ApiResponse<List<CardResponse>> listCards(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                     @PathVariable UUID id) {
        return ApiResponse.success(flashcardService.listCards(principal.getId(), id));
    }

    @PostMapping("/decks/{id}/cards")
    public ApiResponse<CardResponse> createCard(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                @PathVariable UUID id,
                                                @RequestBody CardRequest req) {
        return ApiResponse.success(flashcardService.createCard(principal.getId(), id, req));
    }

    @PutMapping("/cards/{id}")
    public ApiResponse<CardResponse> updateCard(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                @PathVariable UUID id,
                                                @RequestBody CardRequest req) {
        return ApiResponse.success(flashcardService.updateCard(principal.getId(), id, req));
    }

    @DeleteMapping("/cards/{id}")
    public ApiResponse<Void> deleteCard(@AuthenticationPrincipal JwtUserPrincipal principal,
                                        @PathVariable UUID id) {
        flashcardService.deleteCard(principal.getId(), id);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/decks/{id}")
    public ApiResponse<Void> deleteDeck(@AuthenticationPrincipal JwtUserPrincipal principal,
                                        @PathVariable UUID id) {
        flashcardService.deleteDeck(principal.getId(), id);
        return ApiResponse.success(null);
    }
}
