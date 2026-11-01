package com.nihonmukae.backend.flashcard;

import com.nihonmukae.backend.auth.UserRepository;
import com.nihonmukae.backend.dictionary.WordRepository;
import com.nihonmukae.backend.flashcard.dto.CardRequest;
import com.nihonmukae.backend.flashcard.dto.CardResponse;
import com.nihonmukae.backend.flashcard.dto.DeckRequest;
import com.nihonmukae.backend.flashcard.dto.DeckResponse;
import com.nihonmukae.backend.flashcard.dto.FlashcardStatsResponse;
import com.nihonmukae.backend.flashcard.dto.ReviewResultResponse;
import com.nihonmukae.backend.shared.entity.FlashcardReview;
import com.nihonmukae.backend.shared.entity.Flashcard;
import com.nihonmukae.backend.shared.entity.FlashcardDeck;
import com.nihonmukae.backend.shared.entity.Word;
import com.nihonmukae.backend.shared.entity.WordMeaning;
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
    private final WordRepository wordRepository;
    private final SrsService srsService;

    public FlashcardService(DeckRepository deckRepository,
                            CardRepository cardRepository,
                            CardReviewRepository reviewRepository,
                            UserRepository userRepository,
                            WordRepository wordRepository,
                            SrsService srsService) {
        this.wordRepository = wordRepository;
        this.srsService = srsService;
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
        deck = deckRepository.saveAndFlush(deck);
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

    // ─── Card ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CardResponse> listCards(UUID userId, UUID deckId) {
        getOwnedDeck(userId, deckId);
        Map<UUID, ZonedDateTime> nextReviews = new HashMap<>();
        for (FlashcardReview r : reviewRepository.findByUserIdAndCardDeckId(userId, deckId)) {
            nextReviews.put(r.getCard().getId(), r.getNextReviewAt());
        }
        return cardRepository.findByDeckIdOrderByCreatedAtAsc(deckId).stream()
                .map(c -> {
                    CardResponse res = toCardResponse(c);
                    res.setNextReviewAt(nextReviews.get(c.getId()));
                    return res;
                })
                .toList();
    }

    @Transactional
    public CardResponse createCard(UUID userId, UUID deckId, CardRequest req) {
        FlashcardDeck deck = getOwnedDeck(userId, deckId);
        Word word = req.getWordId() == null ? null : wordRepository.findById(req.getWordId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy từ trong từ điển"));

        String front = isBlank(req.getFront()) && word != null ? buildFront(word) : req.getFront();
        String back = isBlank(req.getBack()) && word != null ? buildBack(word) : req.getBack();
        requireContent(front, back);

        Flashcard card = Flashcard.builder()
                .deck(deck)
                .front(front.trim())
                .back(back.trim())
                .imageUrl(req.getImageUrl())
                .word(word)
                .build();
        return toCardResponse(cardRepository.saveAndFlush(card));
    }

    @Transactional
    public CardResponse updateCard(UUID userId, UUID cardId, CardRequest req) {
        Flashcard card = getOwnedCard(userId, cardId);
        requireContent(req.getFront(), req.getBack());
        card.setFront(req.getFront().trim());
        card.setBack(req.getBack().trim());
        card.setImageUrl(req.getImageUrl());
        return toCardResponse(card);
    }

    @Transactional
    public void deleteCard(UUID userId, UUID cardId) {
        cardRepository.delete(getOwnedCard(userId, cardId));
    }

    // ─── Study, review, stats ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CardResponse> getStudyCards(UUID userId, UUID deckId) {
        getOwnedDeck(userId, deckId);
        return cardRepository.findDueCards(deckId, userId, ZonedDateTime.now()).stream()
                .map(this::toCardResponse)
                .toList();
    }

    @Transactional
    public ReviewResultResponse reviewCard(UUID userId, UUID cardId, String rating) {
        int grade = SrsService.gradeFromRating(rating);
        Flashcard card = getOwnedCard(userId, cardId);
        FlashcardReview review = reviewRepository.findByUserIdAndCardId(userId, cardId)
                .orElseGet(() -> FlashcardReview.builder()
                        .user(userRepository.getReferenceById(userId))
                        .card(card)
                        .build());

        ZonedDateTime now = ZonedDateTime.now();
        SrsService.SrsState next = srsService.calculate(
                review.getEaseFactor(), review.getIntervalDays(), review.getRepetitions(), grade, now);
        review.setEaseFactor(next.easeFactor());
        review.setIntervalDays(next.intervalDays());
        review.setRepetitions(next.repetitions());
        review.setNextReviewAt(next.nextReviewAt());
        review.setLastReviewed(now);
        reviewRepository.save(review);

        return ReviewResultResponse.builder()
                .intervalDays(next.intervalDays())
                .repetitions(next.repetitions())
                .easeFactor(next.easeFactor())
                .nextReviewAt(next.nextReviewAt())
                .build();
    }

    @Transactional(readOnly = true)
    public FlashcardStatsResponse getStats(UUID userId) {
        long total = cardRepository.countByDeckUserId(userId);
        long scheduled = reviewRepository.countNotDue(userId, ZonedDateTime.now());
        return FlashcardStatsResponse.builder()
                .totalCards(total)
                .learnedCards(reviewRepository.countLearned(userId))
                .dueToday(total - scheduled)
                .streak(calculateStreak(reviewRepository.findReviewDays(userId)))
                .build();
    }

    // reviewDays: các ngày có ôn tập, mới nhất trước. Streak còn giữ nếu hôm nay
    // hoặc hôm qua có ôn (hôm nay chưa ôn thì chưa bị mất chuỗi)
    static int calculateStreak(List<java.sql.Date> reviewDays) {
        java.time.LocalDate expected = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        if (reviewDays.isEmpty()) {
            return 0;
        }
        if (!reviewDays.get(0).toLocalDate().equals(expected)) {
            expected = expected.minusDays(1);
        }
        int streak = 0;
        for (java.sql.Date d : reviewDays) {
            if (d.toLocalDate().equals(expected)) {
                streak++;
                expected = expected.minusDays(1);
            } else if (d.toLocalDate().isBefore(expected)) {
                break;
            }
        }
        return streak;
    }

    private Flashcard getOwnedCard(UUID userId, UUID cardId) {
        return cardRepository.findByIdAndDeckUserId(cardId, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy flashcard"));
    }

    // Mặt trước: dòng đầu là mặt chữ chính, dòng sau (nếu có) là cách đọc
    private String buildFront(Word word) {
        if (word.getKanji() == null || word.getKanji().isBlank()) {
            return word.getKana();
        }
        return word.getKanji() + "\n" + word.getKana();
    }

    // Mặt sau: nghĩa tiếng Việt đầu tiên, không có thì lấy nghĩa đầu tiên bất kỳ
    private String buildBack(Word word) {
        List<WordMeaning> meanings = word.getMeanings() == null ? List.of() : word.getMeanings();
        return meanings.stream()
                .filter(m -> "vi".equals(m.getLang()))
                .findFirst()
                .or(() -> meanings.stream().findFirst())
                .map(m -> m.getMeaning().replaceFirst("^◆\\s*", "").replaceFirst("[.。]\\s*$", ""))
                .orElse("");
    }

    private void requireContent(String front, String back) {
        if (isBlank(front) || isBlank(back)) {
            throw new IllegalArgumentException("Mặt trước và mặt sau của thẻ không được để trống");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private CardResponse toCardResponse(Flashcard c) {
        return CardResponse.builder()
                .id(c.getId())
                .deckId(c.getDeck().getId())
                .front(c.getFront())
                .back(c.getBack())
                .imageUrl(c.getImageUrl())
                .wordId(c.getWord() == null ? null : c.getWord().getId())
                .createdAt(c.getCreatedAt())
                .build();
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
