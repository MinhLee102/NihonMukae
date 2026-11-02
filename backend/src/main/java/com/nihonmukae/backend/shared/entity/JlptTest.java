package com.nihonmukae.backend.shared.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

/** Một đề thi (theo năm và tháng, hoặc một bộ đề luyện). Câu hỏi trỏ về đề qua questions.test_id. */
@Entity
@Table(name = "jlpt_tests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JlptTest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Short level;

    @Column(length = 500)
    private String title;

    @Column(name = "source_note", length = 200)
    private String sourceNote;

    private Short year;

    private Short month;

    /** File audio của cả phần nghe, phát liền một mạch khi thi thử. */
    @Column(name = "listening_audio_url", length = 500)
    private String listeningAudioUrl;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
