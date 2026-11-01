package com.nihonmukae.backend.practice;

import com.nihonmukae.backend.shared.entity.PracticeAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PracticeAnswerRepository extends JpaRepository<PracticeAnswer, Long> {

    List<PracticeAnswer> findBySessionIdOrderByPositionAsc(UUID sessionId);

    Optional<PracticeAnswer> findBySessionIdAndQuestionId(UUID sessionId, Long questionId);

    // [sessionId, số câu, số câu đúng] cho danh sách lịch sử
    @Query("SELECT a.sessionId, COUNT(a), SUM(CASE WHEN a.correct = true THEN 1 ELSE 0 END) " +
            "FROM PracticeAnswer a WHERE a.sessionId IN :ids GROUP BY a.sessionId")
    List<Object[]> countBySessions(@Param("ids") Collection<UUID> ids);
}
