package com.nihonmukae.backend.dictionary;

import com.nihonmukae.backend.shared.entity.SearchHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    // LEFT JOIN words vì word_id có thể null (search không ra kết quả vẫn
    // ghi lại raw_query) hoặc từ đã bị xoá (ON DELETE SET NULL).
    @Query(value = "SELECT sh.id AS id, sh.word_id AS wordId, w.kanji AS kanji, w.kana AS kana, " +
            "sh.raw_query AS rawQuery, sh.searched_at AS searchedAt " +
            "FROM search_history sh LEFT JOIN words w ON w.id = sh.word_id " +
            "WHERE sh.user_id = :userId " +
            "ORDER BY sh.searched_at DESC",
            countQuery = "SELECT count(*) FROM search_history WHERE user_id = :userId",
            nativeQuery = true)
    Page<SearchHistoryProjection> findHistory(@Param("userId") UUID userId, Pageable pageable);

    // Trả về số dòng đã xoá — dùng để biết có đúng thật sự tồn tại + thuộc
    // về user này không (0 = không tìm thấy hoặc không phải chủ sở hữu).
    long deleteByIdAndUserId(Long id, UUID userId);

    void deleteByUserId(UUID userId);
}