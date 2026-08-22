package com.nihonmukae.backend.shared.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "search_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "word_id")
    private Long wordId; // null nếu search không ra kết quả nào để gắn

    @Column(name = "raw_query", length = 255)
    private String rawQuery;

    @CreationTimestamp
    @Column(name = "searched_at", updatable = false)
    private ZonedDateTime searchedAt;
}