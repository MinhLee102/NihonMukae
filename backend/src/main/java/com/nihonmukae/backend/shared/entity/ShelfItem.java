package com.nihonmukae.backend.shared.entity;

import com.nihonmukae.backend.shelf.dto.ShelfToken;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "shelf_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShelfItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 500)
    private String title;

    // Văn bản gốc user nhập, giữ nguyên xuống dòng
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // Kết quả tách từ và furigana của content, sinh lại mỗi lần content đổi
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_tokens", nullable = false, columnDefinition = "jsonb")
    private List<ShelfToken> contentTokens;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
