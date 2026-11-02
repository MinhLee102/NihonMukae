package com.nihonmukae.backend.shared.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.UUID;

/** Một file audio hoặc ảnh do admin upload. Nội dung nằm ở nơi lưu trữ (ổ đĩa hoặc Google Drive), bảng này chỉ giữ thông tin. */
@Entity
@Table(name = "media_files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaFile {
    @Id
    private UUID id;

    @Column(nullable = false, length = 10)
    private String kind;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String sha256;

    @Column(name = "original_name", length = 255)
    private String originalName;

    @Column(nullable = false, length = 20)
    private String storage;

    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
