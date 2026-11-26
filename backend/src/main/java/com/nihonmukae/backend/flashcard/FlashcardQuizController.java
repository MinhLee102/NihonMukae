package com.nihonmukae.backend.flashcard;

import com.nihonmukae.backend.auth.jwt.JwtUserPrincipal;
import com.nihonmukae.backend.flashcard.dto.QuizDtos.Attempt;
import com.nihonmukae.backend.flashcard.dto.QuizDtos.AttemptRequest;
import com.nihonmukae.backend.flashcard.dto.QuizDtos.QuizQuestion;
import com.nihonmukae.backend.shared.dto.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/flashcard")
public class FlashcardQuizController {

    private final FlashcardQuizService quizService;

    public FlashcardQuizController(FlashcardQuizService quizService) {
        this.quizService = quizService;
    }

    // GET /flashcard/decks/{id}/quiz?kinds=meaning,reading,cloze&count=20
    @GetMapping("/decks/{id}/quiz")
    public ApiResponse<List<QuizQuestion>> quiz(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                @PathVariable UUID id,
                                                @RequestParam(value = "kinds") List<String> kinds,
                                                @RequestParam(value = "count", required = false) Integer count) {
        return ApiResponse.success(quizService.generate(principal.getId(), id, kinds, count));
    }

    @PostMapping("/quiz/attempts")
    public ApiResponse<Attempt> save(@AuthenticationPrincipal JwtUserPrincipal principal,
                                     @RequestBody AttemptRequest req) {
        return ApiResponse.success(quizService.saveAttempt(principal.getId(), req));
    }

    // GET /flashcard/quiz/attempts?deckId=...  : các lượt gần nhất (tối đa 50)
    @GetMapping("/quiz/attempts")
    public ApiResponse<List<Attempt>> attempts(@AuthenticationPrincipal JwtUserPrincipal principal,
                                               @RequestParam(value = "deckId", required = false) UUID deckId,
                                               @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return ApiResponse.success(quizService.attempts(principal.getId(), deckId, limit));
    }
}
