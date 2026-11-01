package com.nihonmukae.backend.flashcard;

import com.nihonmukae.backend.shared.entity.FlashcardDeck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeckRepository extends JpaRepository<FlashcardDeck, UUID> {

    // Find all Flashcard of a specific User
    List<FlashcardDeck> findByUserId(UUID userId);

    List<FlashcardDeck> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // Luôn tìm kèm userId để user không đụng được vào deck của người khác
    Optional<FlashcardDeck> findByIdAndUserId(UUID id, UUID userId);
}
