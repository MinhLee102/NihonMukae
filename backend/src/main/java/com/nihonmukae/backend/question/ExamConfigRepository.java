package com.nihonmukae.backend.question;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Đọc cấu hình đề thi (khối, blueprint, thang điểm). Các bảng này nhỏ, ít đổi và có khóa
 * ghép nên dùng JdbcTemplate thay vì entity.
 */
@Repository
public class ExamConfigRepository {

    public record Block(int blockNo, String name, int durationMin) {
    }

    public record BlueprintRow(int blockNo, short typeId, int quantity, BigDecimal pointsPerQuestion, int sortOrder) {
    }

    public record ScoringSection(String code, List<String> sections, int maxScore, int minScore) {
    }

    private final JdbcTemplate jdbc;

    public ExamConfigRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Block> findBlocks(int level) {
        return jdbc.query("SELECT block_no, name, duration_min FROM exam_blocks WHERE level = ? ORDER BY block_no",
                (rs, i) -> new Block(rs.getInt(1), rs.getString(2), rs.getInt(3)), level);
    }

    public List<BlueprintRow> findBlueprint(int level) {
        return jdbc.query("SELECT block_no, type_id, quantity, points_per_question, sort_order " +
                        "FROM exam_blueprints WHERE level = ? ORDER BY block_no, sort_order",
                (rs, i) -> new BlueprintRow(rs.getInt(1), rs.getShort(2), rs.getInt(3), rs.getBigDecimal(4), rs.getInt(5)),
                level);
    }

    public List<ScoringSection> findScoringSections(int level) {
        return jdbc.query("SELECT code, sections, max_score, min_score FROM exam_scoring_sections WHERE level = ? ORDER BY code",
                (rs, i) -> new ScoringSection(rs.getString(1), toList(rs.getArray(2)), rs.getInt(3), rs.getInt(4)),
                level);
    }

    public Optional<Integer> findPassScore(int level) {
        return jdbc.query("SELECT pass_score FROM exam_levels WHERE level = ?", (rs, i) -> rs.getInt(1), level)
                .stream().findFirst();
    }

    private static List<String> toList(Array array) throws SQLException {
        Object[] values = (Object[]) array.getArray();
        return java.util.Arrays.stream(values).map(String::valueOf).toList();
    }
}
