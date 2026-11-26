package com.nihonmukae.backend.flashcard;

import com.nihonmukae.backend.auth.UserRepository;
import com.nihonmukae.backend.dictionary.DictionaryService;
import com.nihonmukae.backend.dictionary.dto.DictDtos.Entry;
import com.nihonmukae.backend.dictionary.dto.DictDtos.Example;
import com.nihonmukae.backend.dictionary.dto.DictDtos.Meaning;
import com.nihonmukae.backend.flashcard.dto.CardRequest;
import com.nihonmukae.backend.flashcard.dto.CardResponse;
import com.nihonmukae.backend.flashcard.dto.DeckRequest;
import com.nihonmukae.backend.flashcard.dto.DeckResponse;
import com.nihonmukae.backend.flashcard.dto.FlashcardStatsResponse;
import com.nihonmukae.backend.flashcard.dto.ReviewResultResponse;
import com.nihonmukae.backend.shared.entity.FlashcardReview;
import com.nihonmukae.backend.shared.entity.Flashcard;
import com.nihonmukae.backend.shared.entity.FlashcardDeck;
import com.nihonmukae.backend.shared.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Objects;
import java.util.Comparator;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class FlashcardService {

    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final CardReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final DictionaryService dictionaryService;
    private final SrsService srsService;

    public FlashcardService(DeckRepository deckRepository,
                            CardRepository cardRepository,
                            CardReviewRepository reviewRepository,
                            UserRepository userRepository,
                            DictionaryService dictionaryService,
                            SrsService srsService) {
        this.dictionaryService = dictionaryService;
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
        Entry word = req.getWordId() == null ? null : dictionaryService.getEntry(req.getWordId());

        String front = isBlank(req.getFront()) && word != null ? buildFront(word) : req.getFront();
        String back = isBlank(req.getBack()) && word != null ? buildBack(word) : req.getBack();
        requireContent(front, back);

        Flashcard card = Flashcard.builder()
                .deck(deck)
                .front(front.trim())
                .back(back.trim())
                .imageUrl(req.getImageUrl())
                .wordId(word == null ? null : word.id())
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

    private static final int MAX_STUDY_LIMIT = 1000;

    @Transactional(readOnly = true)
    public List<CardResponse> getStudyCards(UUID userId, UUID deckId, String mode, Integer limit) {
        getOwnedDeck(userId, deckId);
        boolean all = "all".equalsIgnoreCase(mode);
        ZonedDateTime now = ZonedDateTime.now();
        Map<UUID, FlashcardReview> reviews = new HashMap<>();
        for (FlashcardReview r : reviewRepository.findByUserIdAndCardDeckId(userId, deckId)) {
            reviews.put(r.getCard().getId(), r);
        }
        List<Flashcard> pool = new ArrayList<>();
        for (Flashcard c : cardRepository.findByDeckIdOrderByCreatedAtAsc(deckId)) {
            if (all || isDue(reviews.get(c.getId()), now)) {
                pool.add(c);
            }
        }
        // Xáo trộn rồi sắp ổn định theo nhóm ưu tiên: trong cùng nhóm thứ tự vẫn ngẫu nhiên
        Collections.shuffle(pool);
        pool.sort(Comparator.comparingInt(c -> priority(reviews.get(c.getId()))));
        if (limit != null && limit > 0 && limit <= MAX_STUDY_LIMIT && pool.size() > limit) {
            pool = pool.subList(0, limit);
        }
        return withDictionary(pool.stream().map(this::toCardResponse).toList());
    }

    static boolean isDue(FlashcardReview review, ZonedDateTime now) {
        return review == null || !review.getNextReviewAt().isAfter(now);
    }

    /**
     * Nhóm ưu tiên khi chọn thẻ ôn (nhỏ hơn đứng trước): 0 lần gần nhất bấm Khó, 1 Bình thường, 2 chưa học,
     * 3 Dễ. Thẻ ôn trước khi có cột last_rating thì đoán từ SM-2: chuỗi nhớ vừa bị reset là Khó, hệ số dễ
     * dưới mức ban đầu là Bình thường.
     */
    static int priority(FlashcardReview review) {
        if (review == null) {
            return 2;
        }
        String last = review.getLastRating();
        if (last == null) {
            if (review.getRepetitions() == null || review.getRepetitions() == 0) {
                return 0;
            }
            return review.getEaseFactor() != null && review.getEaseFactor() < 2.5f ? 1 : 3;
        }
        return switch (last) {
            case "hard" -> 0;
            case "normal" -> 1;
            default -> 3;
        };
    }

    /** Thêm Hán Việt và một câu ví dụ có bản dịch từ từ điển cho các thẻ tạo từ từ điển. */
    private List<CardResponse> withDictionary(List<CardResponse> cards) {
        List<Long> ids = cards.stream().map(CardResponse::getWordId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Entry> entries = new HashMap<>();
        for (Entry e : dictionaryService.getEntries(ids)) {
            entries.put(e.id(), e);
        }
        for (CardResponse card : cards) {
            Entry e = card.getWordId() == null ? null : entries.get(card.getWordId());
            if (e == null) {
                continue;
            }
            card.setHanViet(isBlank(e.hanViet()) ? null : e.hanViet());
            e.examples().stream()
                    .filter(x -> !isBlank(x.sentence()) && !isBlank(x.translation()))
                    .findFirst()
                    .ifPresent(x -> {
                        card.setExampleJa(x.sentence().trim());
                        card.setExampleVi(x.translation().trim());
                    });
        }
        return cards;
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
        review.setLastRating(rating.toLowerCase());
        review.setLastReviewed(now);
        // Ôn thêm thẻ chưa đến hạn không đổi lịch nhắc, trừ khi bấm Khó (đã quên thì cần ôn sớm lại)
        boolean due = review.getId() == null || isDue(review, now);
        if (due || grade < SrsService.GRADE_NORMAL) {
            SrsService.SrsState next = srsService.calculate(
                    review.getEaseFactor(), review.getIntervalDays(), review.getRepetitions(), grade, now);
            review.setEaseFactor(next.easeFactor());
            review.setIntervalDays(next.intervalDays());
            review.setRepetitions(next.repetitions());
            review.setNextReviewAt(next.nextReviewAt());
        }
        reviewRepository.save(review);

        return ReviewResultResponse.builder()
                .intervalDays(review.getIntervalDays())
                .repetitions(review.getRepetitions())
                .easeFactor(review.getEaseFactor())
                .nextReviewAt(review.getNextReviewAt())
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
    private String buildFront(Entry word) {
        if (word.kanji() == null || word.kanji().isBlank()) {
            return word.kana();
        }
        return word.kanji() + "\n" + word.kana();
    }

    // Mặt sau: nghĩa đầu tiên (tiếng Việt, chưa có thì tiếng Anh),
    // kèm 1 câu ví dụ ngẫu nhiên ở dòng thứ hai nếu từ có ví dụ
    private String buildBack(Entry word) {
        String meaning = word.meanings().stream().findFirst()
                .map(Meaning::meaning)
                .map(m -> m.replaceFirst("^◆\\s*", "").replaceFirst("[.。]\\s*$", ""))
                .orElse("");

        List<Example> examples = word.examples().stream()
                .filter(e -> !isBlank(e.sentence()))
                .toList();
        if (examples.isEmpty()) {
            return meaning;
        }
        Example example = examples.get(ThreadLocalRandom.current().nextInt(examples.size()));
        String line = isBlank(example.translation())
                ? example.sentence().trim()
                : example.sentence().trim() + " – " + example.translation().trim();
        return meaning + "\n" + line;
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
                .wordId(c.getWordId())
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
