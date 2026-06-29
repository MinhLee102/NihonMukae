package com.nihonmukae.backend.shared.entity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "example_sentences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExampleSentence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_id", nullable = false)
    private Word word;

    @Column(name = "lang_src", length = 5)
    private String langSrc; // 'ja'

    @Column(name = "lang_tgt", length = 5)
    private String langTgt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String sentence;

    @Column(columnDefinition = "TEXT")
    private String translation;
}
