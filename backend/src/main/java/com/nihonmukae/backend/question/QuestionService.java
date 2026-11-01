package com.nihonmukae.backend.question;

import com.nihonmukae.backend.question.dto.QuestionTypeResponse;
import com.nihonmukae.backend.shared.entity.QuestionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class QuestionService {

    private final QuestionTypeRepository typeRepository;
    private final QuestionRepository questionRepository;

    public QuestionService(QuestionTypeRepository typeRepository, QuestionRepository questionRepository) {
        this.typeRepository = typeRepository;
        this.questionRepository = questionRepository;
    }

    @Transactional(readOnly = true)
    public List<QuestionTypeResponse> listTypes(short level) {
        Map<Short, Long> available = new HashMap<>();
        for (Object[] row : questionRepository.countByTypeForLevel(level)) {
            available.put((Short) row[0], (Long) row[1]);
        }
        return typeRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .filter(t -> t.getLevels().contains(level))
                .map(t -> toResponse(t, available.getOrDefault(t.getId(), 0L)))
                .toList();
    }

    private QuestionTypeResponse toResponse(QuestionType t, long available) {
        return new QuestionTypeResponse(t.getId(), t.getCode(), t.getNameVi(), t.getNameJa(), t.getSection(),
                t.isGroupBased(), available);
    }
}
