package com.nihonmukae.backend.flashcard;

import com.nihonmukae.backend.shared.entity.FlashcardDeck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface DeckRepository extends JpaRepository<FlashcardDeck, UUID> {

    // Find all Flashcard of a specific User
    List<FlashcardDeck> findByUserId(UUID userId);
}
