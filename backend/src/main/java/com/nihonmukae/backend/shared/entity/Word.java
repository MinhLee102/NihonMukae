package com.nihonmukae.backend.shared.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
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

    // id gốc bên mazii_vocab.csv — dùng để trace nguồn, KHÔNG cần dùng trong
    // logic nghiệp vụ thông thường
    @Column(name = "mazii_vocab_id", unique = true)
    private Integer maziiVocabId;

    // kanji/kana thực tế là TEXT trong DB (nới từ VARCHAR(100) vì có từ/thành
    // ngữ Mazii dài hơn 100 ký tự) — không set length ở đây để tránh hiểu nhầm
    @Column
    private String kanji;

    @Column(nullable = false)
    private String kana;

    @Column(length = 200)
    private String romaji;

    // Riêng của Mazii — không có ở JMdict
    @Column(name = "han_viet", columnDefinition = "TEXT")
    private String hanViet;

    // Ký hiệu trọng âm (pitch accent) từ Mazii, vd "chi＼ikida／ihyo–‾"
    @Column(columnDefinition = "TEXT")
    private String pronunciation;

    // Loại từ (danh từ, động từ...) — lấy từ type_word của Mazii
    @Column(columnDefinition = "TEXT")
    private String pos;

    @Column(name = "jlpt_level")
    private Short jlptLevel;

    @Column(name = "frequency_rank")
    private Integer frequencyRank;

    @OneToMany(mappedBy = "word", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @BatchSize(size = 20)
    private List<WordMeaning> meanings;

    @OneToMany(mappedBy = "word", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @BatchSize(size = 20)
    private List<ExampleSentence> examples;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}