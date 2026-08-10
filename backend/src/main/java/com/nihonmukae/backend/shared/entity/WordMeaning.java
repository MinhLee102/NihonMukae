package com.nihonmukae.backend.shared.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "word_meanings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WordMeaning {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_id", nullable = false)
    private Word word;

    @Column(length = 5, nullable = false)
    private String lang;

    @Column(length = 50)
    private String pos;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String meaning;

    @Builder.Default
    @Column(name = "sort_order")
    private Short sortOrder = 0;
}
