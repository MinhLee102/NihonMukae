package com.nihonmukae.backend.auth;

import com.nihonmukae.backend.shared.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    long countByEnabledFalse();

    long countByCreatedAtAfter(java.time.ZonedDateTime since);

    long countByEnabledTrueAndLastLoginAtAfter(java.time.ZonedDateTime since);
}
