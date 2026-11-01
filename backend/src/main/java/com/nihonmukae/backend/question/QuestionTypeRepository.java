package com.nihonmukae.backend.question;

import com.nihonmukae.backend.shared.entity.QuestionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestionTypeRepository extends JpaRepository<QuestionType, Short> {

    List<QuestionType> findByActiveTrueOrderBySortOrderAsc();

    Optional<QuestionType> findByCode(String code);
}
