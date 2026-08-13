package com.nihonmukae.backend.auth;

import com.nihonmukae.backend.shared.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenAndExpiresAtAfter(String token, ZonedDateTime now);
    void deleteByToken(String token);
    void deleteByUserId(UUID userId);
}