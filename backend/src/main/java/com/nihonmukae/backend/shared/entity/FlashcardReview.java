package com.nihonmukae.backend.shared.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "flashcard_reviews", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "card_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashcardReview {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private Flashcard card;

    @Builder.Default
    @Column(name = "ease_factor")
    private Float easeFactor = 2.5f;

    @Builder.Default
    @Column(name = "interval_days")
    private Integer intervalDays = 1;

    @Builder.Default
    private Integer repetitions = 0;

    @Builder.Default
    @Column(name = "next_review_at")
    private ZonedDateTime nextReviewAt = ZonedDateTime.now();

    @Column(name = "last_reviewed")
    private ZonedDateTime lastReviewed;
}
