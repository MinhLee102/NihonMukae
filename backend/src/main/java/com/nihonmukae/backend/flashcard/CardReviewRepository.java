package com.nihonmukae.backend.flashcard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.nihonmukae.backend.shared.entity.FlashcardReview;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface CardReviewRepository extends JpaRepository<FlashcardReview, UUID> {

    // Truy vấn lấy danh sách card cần review ngày hôm nay dựa trên thuật toán SM-2
    @Query("SELECT r FROM FlashcardReview r " +
            "JOIN FETCH r.card c " +
            "WHERE r.user.id = :userId " +
            "AND r.nextReviewAt <= :now")
    List<FlashcardReview> findCardsToReviewToday(@Param("userId") UUID userId, @Param("now") ZonedDateTime now);

    // "Đã học" = đã review thành công ít nhất 1 lần (repetitions > 0)
    @Query("SELECT r.card.deck.id, COUNT(r) FROM FlashcardReview r " +
            "WHERE r.user.id = :userId AND r.repetitions > 0 GROUP BY r.card.deck.id")
    List<Object[]> countLearnedByDeck(@Param("userId") UUID userId);

    // Card đã có lịch ôn nhưng chưa đến hạn. Số card cần ôn = tổng card trừ số này
    @Query("SELECT r.card.deck.id, COUNT(r) FROM FlashcardReview r " +
            "WHERE r.user.id = :userId AND r.nextReviewAt > :now GROUP BY r.card.deck.id")
    List<Object[]> countNotDueByDeck(@Param("userId") UUID userId, @Param("now") ZonedDateTime now);

    @Query("SELECT COUNT(r) FROM FlashcardReview r " +
            "WHERE r.user.id = :userId AND r.card.deck.id = :deckId AND r.repetitions > 0")
    long countLearnedInDeck(@Param("userId") UUID userId, @Param("deckId") UUID deckId);

    @Query("SELECT COUNT(r) FROM FlashcardReview r " +
            "WHERE r.user.id = :userId AND r.card.deck.id = :deckId AND r.nextReviewAt > :now")
    long countNotDueInDeck(@Param("userId") UUID userId, @Param("deckId") UUID deckId, @Param("now") ZonedDateTime now);
}
