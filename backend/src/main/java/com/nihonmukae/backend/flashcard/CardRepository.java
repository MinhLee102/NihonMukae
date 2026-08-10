package com.nihonmukae.backend.flashcard;

import com.nihonmukae.backend.shared.entity.Flashcard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface CardRepository extends JpaRepository<Flashcard, UUID> {

    // Lấy toàn bộ danh sách các thẻ flashcard nằm trong một bộ deck nhất định
    List<Flashcard> findByDeckId(UUID deckId);
}