package com.nihonmukae.backend.media;

import com.nihonmukae.backend.shared.entity.MediaFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MediaFileRepository extends JpaRepository<MediaFile, UUID> {

    Optional<MediaFile> findBySha256(String sha256);

    long countByUploadedByAndCreatedAtAfter(UUID uploadedBy, ZonedDateTime since);

    @Query("SELECT COALESCE(SUM(m.sizeBytes), 0) FROM MediaFile m")
    long totalBytes();
}
