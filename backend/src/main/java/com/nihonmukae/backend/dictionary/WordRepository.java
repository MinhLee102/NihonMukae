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
     * QUAN TRỌNG: tham số `tsQuery` PHẢI là chuỗi tsquery đã build sẵn (vd
     * "地域:* & 代表:*"), KHÔNG được truyền thẳng input thô của người dùng —
     * to_tsquery() sẽ ném lỗi cú pháp nếu chuỗi chứa khoảng trắng hoặc ký tự
     * đặc biệt của tsquery (&, |, !, :, ', *, ()).
     * Việc sanitize + build chuỗi này nằm ở DictionaryService.buildPrefixTsQuery().
     *
     * Dùng ':*' (prefix match) vì tiếng Nhật không có khoảng trắng giữa các
     * ký tự — search_vector coi cả 1 từ kanji/kana là MỘT token duy nhất, nên
     * nếu không có ':*' thì chỉ match được đúng y hệt token, không match được
     * kiểu "gõ 1 phần của từ" (autocomplete).
     */
    @Query(value = "SELECT * FROM words w " +
            "WHERE w.search_vector @@ to_tsquery('simple', :tsQuery) " +
            "ORDER BY " +
            // 1) khớp CHÍNH XÁC kanji hoặc kana luôn lên đầu tuyệt đối
            "  CASE WHEN w.kanji = :rawQuery OR w.kana = :rawQuery THEN 0 ELSE 1 END, " +
            // 2) trong số còn lại, điểm ts_rank cao hơn trước
            "  ts_rank(w.search_vector, to_tsquery('simple', :tsQuery)) DESC, " +
            // 3) từ ngắn hơn (khả năng cao là từ thông dụng, ít ghép dài) ưu tiên hơn
            "  length(coalesce(w.kanji, w.kana)) ASC, " +
            // 4) frequency_rank: hiện luôn NULL (Mazii import chưa có nguồn gán giá
            //    trị này) nên không có tác dụng thực tế bây giờ, giữ lại phòng khi
            //    sau này có nguồn dữ liệu tần suất thật để populate cột này
            "  w.frequency_rank ASC NULLS LAST",
            countQuery = "SELECT count(*) FROM words w " +
                    "WHERE w.search_vector @@ to_tsquery('simple', :tsQuery)",
            nativeQuery = true)
    Page<Word> searchFullText(@Param("tsQuery") String tsQuery, @Param("rawQuery") String rawQuery, Pageable pageable);

    /**
     * Fallback fuzzy search bằng trigram (pg_trgm) — dùng khi searchFullText
     * không ra kết quả nào (vd người dùng gõ sai chính tả). Dựa vào
     * idx_words_kanji_trgm / idx_words_kana_trgm (xem migration
     * V__add_kana_trgm_index.sql — kana trước đây CHƯA có trigram index).
     */
    @Query(value = "SELECT * FROM words w " +
            "WHERE w.kanji % :query OR w.kana % :query " +
            "ORDER BY GREATEST(similarity(coalesce(w.kanji, ''), :query), similarity(w.kana, :query)) DESC " +
            "LIMIT :limit",
            nativeQuery = true)
    List<Word> searchByTrigramSimilarity(@Param("query") String query, @Param("limit") int limit);

    /**
     * Search theo NGHĨA tiếng Việt (word_meanings.meaning_search_vector) —
     * xem migration V__add_meaning_search_vector.sql. Khác searchFullText():
     * đây match theo nghĩa, không phải mặt chữ Nhật. 1 từ có thể có nhiều
     * dòng meaning khớp — lấy rank tốt nhất trong số các nghĩa khớp của mỗi
     * từ để sắp xếp (subquery GROUP BY word_id, MAX(ts_rank)).
     *
     * tsQuery build tương tự searchFullText() (dùng chung buildPrefixTsQuery
     * ở Service) — cũng dùng prefix match (:*) vì lợi cho autocomplete, dù
     * tiếng Việt có khoảng trắng thật nên về lý thuyết plainto_tsquery cũng
     * work được, nhưng giữ nhất quán 1 kiểu tsquery cho cả 2 method.
     */
    @Query(value = "SELECT w.* FROM words w " +
            "JOIN (" +
            "  SELECT wm.word_id, MAX(" +
            // 1) khớp CHÍNH XÁC cả câu nghĩa (sau khi bỏ dấu "◆ " đầu + dấu câu cuối)
            //    -> điểm rất cao, luôn thắng mọi tiêu chí khác. Fix cho case
            //    "Nhật Bản" phải ra đúng nghĩa "Nhật Bản", không phải nghĩa liên quan.
            "    CASE WHEN lower(trim(both '.。 ' from trim(leading '◆ ' from wm.meaning))) = lower(:rawQuery) THEN 1000000 ELSE 0 END" +
            // 2) ts_rank_cd: cover-density, ưu tiên từ khớp nằm gần nhau/đúng thứ tự
            "    + ts_rank_cd(wm.meaning_search_vector, to_tsquery('simple', :tsQuery)) * 100" +
            // 3) frequency_rank THẬT từ JMdict (scripts/jmdict/backfill_frequency.py)
            //    -> QUAN TRỌNG: nằm NGAY TRONG công thức điểm (không phải ORDER BY phụ
            //    bên ngoài) để áp đảo được các chênh lệch vặt vãnh như dấu câu/độ dài
            //    câu nghĩa (bug thực tế gặp phải: 日本 vs 日本国 exact-match bằng nhau
            //    nhưng lệch 1 ký tự độ dài do dấu "." khiến ORDER BY phụ trước đây
            //    không bao giờ có cơ hội can thiệp vì 2 rank không còn hòa tuyệt đối).
            //    Trừ giá trị lớn (coalesce 999999 cho từ chưa có/không có trong JMdict)
            //    nên rank càng NHỎ (phổ biến) càng ít bị trừ = điểm càng cao.
            "    - coalesce(w2.frequency_rank, 999999)" +
            // 4) câu nghĩa càng ngắn càng ưu tiên — hạ trọng số xuống rất nhỏ, chỉ còn
            //    tác dụng tie-break khi frequency_rank cũng hòa (cả 2 cùng NULL)
            "    - length(wm.meaning) * 0.01" +
            "  ) AS rank " +
            "  FROM word_meanings wm " +
            "  JOIN words w2 ON w2.id = wm.word_id" +
            "  WHERE wm.meaning_search_vector @@ to_tsquery('simple', :tsQuery) " +
            "  GROUP BY wm.word_id" +
            ") ranked ON ranked.word_id = w.id " +
            "ORDER BY ranked.rank DESC, length(coalesce(w.kanji, w.kana)) ASC",
            countQuery = "SELECT count(*) FROM (" +
                    "  SELECT DISTINCT wm.word_id FROM word_meanings wm " +
                    "  WHERE wm.meaning_search_vector @@ to_tsquery('simple', :tsQuery)" +
                    ") t",
            nativeQuery = true)
    Page<Word> searchByMeaning(@Param("tsQuery") String tsQuery, @Param("rawQuery") String rawQuery, Pageable pageable);
}