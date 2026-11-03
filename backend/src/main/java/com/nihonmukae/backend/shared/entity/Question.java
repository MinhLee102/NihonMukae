package com.nihonmukae.backend.shared.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/** 1 câu hỏi trắc nghiệm trong kho. Đáp án và giải thích không bao giờ trả cho client trước khi được phép. */
@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "type_id", nullable = false)
    private Short typeId;

    @Column(nullable = false)
    private Short level;

    @Column(name = "test_id")
    private Long testId;

    @Column(name = "test_order")
    private Short testOrder;

    @Column(name = "group_id")
    private Long groupId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(name = "audio_url", columnDefinition = "TEXT")
    private String audioUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> options;

    // 'A' đến 'D'
    @Column(nullable = false, columnDefinition = "CHAR(1)")
    private String correct;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    // Bản chữ của câu nghe (tiếng Nhật), chỉ hiện sau khi nộp bài
    @Column(columnDefinition = "TEXT")
    private String transcript;

    @Column(nullable = false, length = 30)
    private String source;

    @Column(name = "content_hash", nullable = false, unique = true, length = 64, columnDefinition = "CHAR(64)")
    private String contentHash;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
