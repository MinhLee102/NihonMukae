package com.nihonmukae.backend.shared.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Một phiên làm bài: ôn theo dạng bài (practice) hoặc thi JLPT (exam). */
@Entity
@Table(name = "practice_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeSession {

    public static final String MODE_PRACTICE = "practice";
    public static final String MODE_EXAM = "exam";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_SUBMITTED = "submitted";

    /** Trạng thái 1 khối thi (practice chỉ có 1 khối, deadline null nếu không giới hạn giờ). */
    public record BlockState(Integer blockNo, String name, Integer durationMin,
                             ZonedDateTime startedAt, ZonedDateTime deadline, ZonedDateTime submittedAt) {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 10)
    private String mode;

    @Column(nullable = false)
    private Short level;

    @Column(length = 500)
    private String title;

    @Column(name = "test_id")
    private Long testId;

    @Column(name = "is_random", nullable = false)
    private boolean random;

    /** Đề cung cấp audio cả phần nghe (luyện nguyên bộ nghe, hoặc đề thi thử ngẫu nhiên). */
    @Column(name = "listening_test_id")
    private Long listeningTestId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "type_ids", columnDefinition = "smallint[]")
    private List<Short> typeIds;

    @Column(name = "question_count")
    private Short questionCount;

    @Column(name = "time_limit_min")
    private Short timeLimitMin;

    @Column(name = "feedback_mode", nullable = false, length = 10)
    private String feedbackMode;

    @Column(nullable = false, length = 15)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "block_state", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<BlockState> blockState = new ArrayList<>();

    // Nội dung câu hỏi (kèm đáp án) và kết quả, chụp lúc nộp bài
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> snapshot;

    private BigDecimal score;

    @Column(name = "max_score")
    private BigDecimal maxScore;

    @Column(name = "started_at", nullable = false)
    private ZonedDateTime startedAt;

    @Column(name = "finished_at")
    private ZonedDateTime finishedAt;
}
