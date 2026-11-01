package com.nihonmukae.backend.question;

import com.nihonmukae.backend.shared.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    Optional<Question> findByContentHash(String contentHash);

    List<Question> findByGroupIdInAndActiveTrueOrderByTestOrderAscIdAsc(Collection<Long> groupIds);

    // Câu đứng riêng (không thuộc nhóm) của 1 dạng bài, dùng để random
    @Query("SELECT q.id FROM Question q WHERE q.typeId = :typeId AND q.level = :level AND q.active = true AND q.groupId IS NULL")
    List<Long> findStandaloneIds(@Param("typeId") short typeId, @Param("level") short level);

    // Số câu có sẵn theo dạng bài và level (đếm cả câu thuộc nhóm), để hiển thị và cảnh báo thiếu câu
    @Query("SELECT q.typeId, COUNT(q) FROM Question q WHERE q.level = :level AND q.active = true GROUP BY q.typeId")
    List<Object[]> countByTypeForLevel(@Param("level") short level);

    List<Question> findByTestIdAndActiveTrueOrderByTestOrderAscIdAsc(Long testId);

    @Query("SELECT q.testId, COUNT(q) FROM Question q WHERE q.level = :level AND q.active = true AND q.testId IS NOT NULL GROUP BY q.testId")
    List<Object[]> countByTestForLevel(@Param("level") short level);

    // [testId, section, số câu] của các đề thi ở 1 level, để biết đề có những phần nào
    @Query("SELECT q.testId, t.section, COUNT(q) FROM Question q JOIN QuestionType t ON t.id = q.typeId " +
            "WHERE q.level = :level AND q.active = true AND q.testId IS NOT NULL GROUP BY q.testId, t.section")
    List<Object[]> countByTestAndSection(@Param("level") short level);

    long countByActiveTrue();
}
