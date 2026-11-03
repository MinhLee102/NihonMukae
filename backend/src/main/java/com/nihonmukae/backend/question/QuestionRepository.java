package com.nihonmukae.backend.question;

import com.nihonmukae.backend.shared.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long>, JpaSpecificationExecutor<Question> {

    Optional<Question> findByContentHash(String contentHash);

    List<Question> findByGroupIdInAndActiveTrueOrderByTestOrderAscIdAsc(Collection<Long> groupIds);

    // Câu đứng riêng (không thuộc nhóm) của 1 dạng bài, dùng để random.
    // Câu nghe của đề có audio cả phần nghe không có audio riêng nên chỉ luyện được theo nguyên bộ, loại khỏi đây.
    @Query("SELECT q.id FROM Question q WHERE q.typeId = :typeId AND q.level = :level AND q.active = true AND q.groupId IS NULL " +
            "AND NOT EXISTS (SELECT 1 FROM JlptTest jt, QuestionType ty WHERE jt.id = q.testId AND jt.listeningAudioUrl IS NOT NULL AND ty.id = q.typeId AND ty.section = 'chokai')")
    List<Long> findStandaloneIds(@Param("typeId") short typeId, @Param("level") short level);

    // Số câu có sẵn theo dạng bài và level (đếm cả câu thuộc nhóm), để hiển thị và cảnh báo thiếu câu
    @Query("SELECT q.typeId, COUNT(q) FROM Question q WHERE q.level = :level AND q.active = true " +
            "AND NOT EXISTS (SELECT 1 FROM JlptTest jt, QuestionType ty WHERE jt.id = q.testId AND jt.listeningAudioUrl IS NOT NULL AND ty.id = q.typeId AND ty.section = 'chokai') GROUP BY q.typeId")
    List<Object[]> countByTypeForLevel(@Param("level") short level);

    List<Question> findByTestIdAndActiveTrueOrderByTestOrderAscIdAsc(Long testId);

    @Query("SELECT q.testId, COUNT(q) FROM Question q WHERE q.level = :level AND q.active = true AND q.testId IS NOT NULL GROUP BY q.testId")
    List<Object[]> countByTestForLevel(@Param("level") short level);

    // [testId, section, số câu] của các đề thi ở 1 level, để biết đề có những phần nào
    @Query("SELECT q.testId, t.section, COUNT(q) FROM Question q JOIN QuestionType t ON t.id = q.typeId " +
            "WHERE q.level = :level AND q.active = true AND q.testId IS NOT NULL GROUP BY q.testId, t.section")
    List<Object[]> countByTestAndSection(@Param("level") short level);

    // Tắt toàn bộ câu của 1 nguồn (dữ liệu mẫu) trước khi nạp lại phiên bản mới
    @Modifying
    @Query("UPDATE Question q SET q.active = false WHERE q.source = :source")
    int deactivateBySource(@Param("source") String source);

    // [section, level, số câu] của các câu đang hoạt động, cho thẻ thống kê của trang quản trị
    @Query("SELECT t.section, q.level, COUNT(q) FROM Question q JOIN QuestionType t ON t.id = q.typeId " +
            "WHERE q.active = true GROUP BY t.section, q.level")
    List<Object[]> countBySectionAndLevel();

    long countByActiveTrue();
}
