package com.nihonmukae.backend.shared.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.ZonedDateTime;
import java.util.List;

@Entity
@Table(name = "words", indexes = {
        @Index(name = "idx_words_kanji", columnList = "kanji"),
        @Index(name = "idx_words_kana", columnList = "kana")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Word {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "jmdict_id", unique = true)
    private Integer jmdictId;

    @Column(length = 100)
    private String kanji;

    @Column(length = 100, nullable = false)
    private String kana;

    @Column(length = 200)
    private String romaji;

    @Column(name = "jlpt_level")
    private Short jlptLevel;

    @Column(name = "frequency_rank")
    private Integer frequencyRank;

    @OneToMany(mappedBy = "word", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<WordMeaning> meanings;

    @OneToMany(mappedBy = "word", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ExampleSentence> examples;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
