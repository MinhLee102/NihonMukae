package com.nihonmukae.backend.flashcard;

import com.nihonmukae.backend.shared.entity.Flashcard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface CardRepository extends JpaRepository<Flashcard, UUID> {

    // Lấy toàn bộ danh sách các thẻ flashcard nằm trong một bộ deck nhất định
    List<Flashcard> findByDeckId(UUID deckId);

    long countByDeckId(UUID deckId);

    // [deckId, số card] cho mọi deck của user — dùng cho trang danh sách deck
    @Query("SELECT c.deck.id, COUNT(c) FROM Flashcard c WHERE c.deck.user.id = :userId GROUP BY c.deck.id")
    List<Object[]> countCardsByDeck(@Param("userId") UUID userId);
}
