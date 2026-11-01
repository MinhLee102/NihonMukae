package com.nihonmukae.backend.shared.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/** Dạng bài (vd kanji_reading). Phần thi (moji, goi, bunpou, dokkai, chokai) suy ra từ cột section. */
@Entity
@Table(name = "question_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name_vi", nullable = false, length = 100)
    private String nameVi;

    @Column(name = "name_ja", length = 100)
    private String nameJa;

    @Column(nullable = false, length = 20)
    private String section;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "smallint[]")
    private List<Short> levels;

    @Column(name = "option_count", nullable = false)
    private Short optionCount;

    // true: đơn vị random là nhóm (đoạn văn hoặc audio dùng chung)
    @Column(name = "is_group_based", nullable = false)
    private boolean groupBased;

    @Column(name = "sort_order", nullable = false)
    private Short sortOrder;

    // Đề bài chung của dạng bài, hiện phía trên câu hỏi như "問題 N" trong đề JLPT
    @Column(name = "instruction_ja", columnDefinition = "TEXT")
    private String instructionJa;

    @Column(name = "instruction_vi", columnDefinition = "TEXT")
    private String instructionVi;

    @Column(name = "is_active", nullable = false)
    private boolean active;
}
