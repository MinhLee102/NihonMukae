package com.nihonmukae.backend.question;

import com.nihonmukae.backend.shared.entity.JlptTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JlptTestRepository extends JpaRepository<JlptTest, Long> {

    List<JlptTest> findByLevelAndActiveTrueOrderByYearDescMonthDescIdDesc(short level);

    /** Đề có audio cả phần nghe, dùng để luyện nguyên bộ nghe. */
    List<JlptTest> findByLevelAndActiveTrueAndListeningAudioUrlIsNotNull(short level);

    Optional<JlptTest> findByLevelAndYearAndMonth(short level, Short year, Short month);

    Optional<JlptTest> findByLevelAndTitle(short level, String title);
}
