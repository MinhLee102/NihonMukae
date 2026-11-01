package com.nihonmukae.backend.shared.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * 1 dòng cho mỗi câu trong phiên. Được tạo sẵn lúc bắt đầu phiên (selected = null) nên
 * cũng là danh sách câu hỏi và thứ tự của phiên.
 */
@Entity
@Table(name = "practice_answers", uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "position"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    // null nếu câu đã bị xóa khỏi kho (snapshot vẫn còn nội dung)
    @Column(name = "question_id")
    private Long questionId;

    @Column(nullable = false)
    private Short position;

    @Column(name = "block_no", nullable = false)
    private Short blockNo;

    @Column(name = "type_id")
    private Short typeId;

    @Column(nullable = false)
    private BigDecimal points;

    @Column(columnDefinition = "CHAR(1)")
    private String selected;

    @Column(name = "is_correct")
    private Boolean correct;

    @Column(nullable = false)
    private boolean locked;

    @Column(name = "answered_at")
    private ZonedDateTime answeredAt;
}
