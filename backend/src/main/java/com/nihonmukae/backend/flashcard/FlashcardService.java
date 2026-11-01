package com.nihonmukae.backend.flashcard;

import com.nihonmukae.backend.auth.UserRepository;
import com.nihonmukae.backend.flashcard.dto.DeckRequest;
import com.nihonmukae.backend.flashcard.dto.DeckResponse;
import com.nihonmukae.backend.shared.entity.FlashcardDeck;
import com.nihonmukae.backend.shared.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FlashcardService {

    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final CardReviewRepository reviewRepository;
    private final UserRepository userRepository;

    public FlashcardService(DeckRepository deckRepository,
                            CardRepository cardRepository,
                            CardReviewRepository reviewRepository,
                            UserRepository userRepository) {
        this.deckRepository = deckRepository;
        this.cardRepository = cardRepository;
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
    }

    // ─── Deck ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DeckResponse> listDecks(UUID userId) {
        ZonedDateTime now = ZonedDateTime.now();
        Map<UUID, Long> cardCounts = toCountMap(cardRepository.countCardsByDeck(userId));
        Map<UUID, Long> learnedCounts = toCountMap(reviewRepository.countLearnedByDeck(userId));
        Map<UUID, Long> scheduledCounts = toCountMap(reviewRepository.countNotDueByDeck(userId, now));

        return deckRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(d -> {
                    long total = cardCounts.getOrDefault(d.getId(), 0L);
                    long due = total - scheduledCounts.getOrDefault(d.getId(), 0L);
                    return toDeckResponse(d, total, learnedCounts.getOrDefault(d.getId(), 0L), due);
                })
                .toList();
    }

    @Transactional
    public DeckResponse createDeck(UUID userId, DeckRequest req) {
        FlashcardDeck deck = FlashcardDeck.builder()
                .user(userRepository.getReferenceById(userId))
                .name(req.getName().trim())
                .description(req.getDescription())
                .color(req.getColor())
                .icon(req.getIcon())
                .build();
        deck = deckRepository.save(deck);
        return toDeckResponse(deck, 0, 0, 0);
    }

    @Transactional
    public DeckResponse updateDeck(UUID userId, UUID deckId, DeckRequest req) {
        FlashcardDeck deck = getOwnedDeck(userId, deckId);
        deck.setName(req.getName().trim());
        deck.setDescription(req.getDescription());
        deck.setColor(req.getColor());
        deck.setIcon(req.getIcon());
        long total = cardRepository.countByDeckId(deckId);
        long learned = reviewRepository.countLearnedInDeck(userId, deckId);
        long scheduled = reviewRepository.countNotDueInDeck(userId, deckId, ZonedDateTime.now());
        return toDeckResponse(deck, total, learned, total - scheduled);
    }

    @Transactional
    public void deleteDeck(UUID userId, UUID deckId) {
        deckRepository.delete(getOwnedDeck(userId, deckId));
    }

    FlashcardDeck getOwnedDeck(UUID userId, UUID deckId) {
        return deckRepository.findByIdAndUserId(deckId, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bộ flashcard"));
    }

    private DeckResponse toDeckResponse(FlashcardDeck d, long cardCount, long learned, long due) {
        return DeckResponse.builder()
                .id(d.getId())
                .name(d.getName())
                .description(d.getDescription())
                .color(d.getColor())
                .icon(d.getIcon())
                .cardCount(cardCount)
                .learnedCount(learned)
                .dueCount(due)
                .createdAt(d.getCreatedAt())
                .build();
    }

    private Map<UUID, Long> toCountMap(List<Object[]> rows) {
        Map<UUID, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((UUID) row[0], (Long) row[1]);
        }
        return map;
    }
}
