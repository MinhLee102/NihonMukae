package com.nihonmukae.backend.shelf;

import com.nihonmukae.backend.shared.entity.ShelfItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShelfRepository extends JpaRepository<ShelfItem, UUID> {

    List<ShelfItem> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    // Luôn tìm kèm userId để user không đụng được vào bài của người khác
    Optional<ShelfItem> findByIdAndUserId(UUID id, UUID userId);
}
