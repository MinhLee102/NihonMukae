package com.nihonmukae.backend.dictionary;

import com.nihonmukae.backend.shared.entity.Word;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * Full-Text Search có phân trang, dùng PostgreSQL to_tsquery + ts_rank.
     *
     * Tham số `tsQuery` PHẢI là chuỗi tsquery đã build sẵn (vd
     * "地域:* & 代表:*"), KHÔNG được truyền thẳng input thô của người dùng —
     * to_tsquery() sẽ ném lỗi cú pháp nếu chuỗi chứa khoảng trắng hoặc ký tự
     * đặc biệt của tsquery (&, |, !, :, ', *, ()).
     * Việc sanitize + build chuỗi ở DictionaryService.buildPrefixTsQuery().
     *
     * Dùng ':*' (prefix match) vì tiếng Nhật không có khoảng trắng giữa các
     * ký tự — search_vector coi cả 1 từ kanji/kana là MỘT token duy nhất, nên
     * nếu không có ':*' thì chỉ match được đúng y hệt token, không match được
     * kiểu "gõ 1 phần của từ" (autocomplete).
     */
    @Query(value = "SELECT * FROM words w " +
            "WHERE w.search_vector @@ to_tsquery('simple', :tsQuery) " +
            "ORDER BY ts_rank(w.search_vector, to_tsquery('simple', :tsQuery)) DESC, " +
            "w.frequency_rank ASC NULLS LAST",
            countQuery = "SELECT count(*) FROM words w " +
                    "WHERE w.search_vector @@ to_tsquery('simple', :tsQuery)",
            nativeQuery = true)
    Page<Word> searchFullText(@Param("tsQuery") String tsQuery, Pageable pageable);

    /**
     * Fallback fuzzy search bằng trigram (pg_trgm) — dùng khi searchFullText
     * không ra kết quả nào (vd người dùng gõ sai chính tả). Dựa vào
     * idx_words_kanji_trgm / idx_words_kana_trgm để tìm các từ có similarity cao nhất với input.
     */
    @Query(value = "SELECT * FROM words w " +
            "WHERE w.kanji % :query OR w.kana % :query " +
            "ORDER BY GREATEST(similarity(coalesce(w.kanji, ''), :query), similarity(w.kana, :query)) DESC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Word> searchByTrigramSimilarity(@Param("query") String query, @Param("limit") int limit);
}