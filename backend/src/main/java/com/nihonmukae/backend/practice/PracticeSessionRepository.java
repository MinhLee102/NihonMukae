package com.nihonmukae.backend.practice;

import com.nihonmukae.backend.shared.entity.PracticeSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PracticeSessionRepository extends JpaRepository<PracticeSession, UUID> {

    // Luôn tìm kèm userId để user không đụng được vào phiên của người khác
    Optional<PracticeSession> findByIdAndUserId(UUID id, UUID userId);

    List<PracticeSession> findByUserIdOrderByStartedAtDesc(UUID userId);

    List<PracticeSession> findByUserIdAndModeOrderByStartedAtDesc(UUID userId, String mode);
}
