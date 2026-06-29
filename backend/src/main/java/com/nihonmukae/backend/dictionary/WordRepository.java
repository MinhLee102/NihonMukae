package com.nihonmukae.backend.dictionary;

import com.nihonmukae.backend.shared.entity.Word;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface WordRepository extends JpaRepository<Word, Long> {

    // Tối ưu hóa câu truy vấn inline lookup cho popup (My Shelf, JLPT) chỉ trong 1 single query
    @Query("SELECT DISTINCT w FROM Word w " +
            "LEFT JOIN FETCH w.meanings m " +
            "LEFT JOIN FETCH w.examples e " +
            "WHERE (w.kanji = :kanji AND w.kana = :kana) " +
            "OR (w.kanji IS NULL AND w.kana = :kana)")
    Optional<Word> findForInlineLookup(@Param("kanji") String kanji, @Param("kana") String kana);

    // Full-Text Search PostgreSQL kết hợp với Trigram Index cho thanh tìm kiếm chính
    @Query(value = "SELECT * FROM words w " +
            "WHERE w.search_vector @@ to_tsquery('simple', :query) " +
            "ORDER BY ts_rank(w.search_vector, to_tsquery('simple', :query)) DESC, w.frequency_rank ASC",
            nativeQuery = true)
    List<Word> searchFullText(@Param("query") String query);
}