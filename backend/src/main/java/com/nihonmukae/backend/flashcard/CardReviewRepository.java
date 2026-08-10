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
}
