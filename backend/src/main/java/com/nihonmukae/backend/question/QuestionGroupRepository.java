package com.nihonmukae.backend.question;

import com.nihonmukae.backend.shared.entity.QuestionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionGroupRepository extends JpaRepository<QuestionGroup, Long> {

    @Modifying
    @Query("UPDATE QuestionGroup g SET g.active = false WHERE g.source = :source")
    int deactivateBySource(@Param("source") String source);

    Optional<QuestionGroup> findByContentHash(String contentHash);

    @Query("SELECT g.id FROM QuestionGroup g WHERE g.typeId = :typeId AND g.level = :level AND g.active = true")
    List<Long> findActiveIds(@Param("typeId") short typeId, @Param("level") short level);
}
