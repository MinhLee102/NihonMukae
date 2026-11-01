package com.nihonmukae.backend.flashcard;

import com.nihonmukae.backend.shared.entity.Flashcard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CardRepository extends JpaRepository<Flashcard, UUID> {

    // Lấy toàn bộ danh sách các thẻ flashcard nằm trong một bộ deck nhất định
    List<Flashcard> findByDeckId(UUID deckId);

    List<Flashcard> findByDeckIdOrderByCreatedAtAsc(UUID deckId);

    // Tìm kèm chủ sở hữu deck để user không sửa/xóa được card của người khác
    Optional<Flashcard> findByIdAndDeckUserId(UUID id, UUID userId);

    // Card cần ôn: chưa có lịch (chưa học lần nào) hoặc đã đến hạn
    @Query("SELECT c FROM Flashcard c WHERE c.deck.id = :deckId AND NOT EXISTS (" +
            "SELECT r FROM FlashcardReview r WHERE r.card = c AND r.user.id = :userId AND r.nextReviewAt > :now) " +
            "ORDER BY c.createdAt ASC")
    List<Flashcard> findDueCards(@Param("deckId") UUID deckId,
                                 @Param("userId") UUID userId,
                                 @Param("now") ZonedDateTime now);

    long countByDeckUserId(UUID userId);

    long countByDeckId(UUID deckId);

    // [deckId, số card] cho mọi deck của user — dùng cho trang danh sách deck
    @Query("SELECT c.deck.id, COUNT(c) FROM Flashcard c WHERE c.deck.user.id = :userId GROUP BY c.deck.id")
    List<Object[]> countCardsByDeck(@Param("userId") UUID userId);
}
