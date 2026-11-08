package com.nihonmukae.backend.practice;

import com.nihonmukae.backend.practice.dto.ResultDtos.Result;
import com.nihonmukae.backend.practice.dto.ResultDtos.ResultQuestion;
import com.nihonmukae.backend.shelf.ShelfService;
import com.nihonmukae.backend.shelf.dto.ShelfItemResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Biến kết quả (snapshot) của một phiên đã nộp thành bài My Shelf. */
@Service
public class PracticeShelfService {

    public record SaveRequest(String scope, Integer position) {
    }

    private final PracticeSessionService sessions;
    private final ShelfService shelf;

    public PracticeShelfService(PracticeSessionService sessions, ShelfService shelf) {
        this.sessions = sessions;
        this.shelf = shelf;
    }

    @Transactional
    public List<ShelfItemResponse> save(UUID userId, UUID sessionId, SaveRequest req) {
        Result result = sessions.result(userId, sessionId);
        String scope = req.scope() == null ? "" : req.scope();
        List<Integer> positions;
        String suffix;
        switch (scope) {
            case "wrong" -> {
                positions = result.questions().stream().filter(q -> !q.correct()).map(ResultQuestion::position).toList();
                suffix = "câu sai";
            }
            case "all" -> {
                positions = result.questions().stream().map(ResultQuestion::position).toList();
                suffix = "cả bài";
            }
            case "question" -> {
                if (req.position() == null) {
                    throw new IllegalArgumentException("Thiếu số câu cần lưu");
                }
                positions = List.of(req.position());
                suffix = "câu " + req.position();
            }
            default -> throw new IllegalArgumentException("scope phải là wrong, all hoặc question");
        }
        if (positions.isEmpty()) {
            throw new IllegalArgumentException("Không có câu nào để lưu");
        }
        // Cả bài thường vượt giới hạn một mục My Shelf nên được tách thành nhiều mục theo phần
        List<ShelfTextBuilder.Part> parts = ShelfTextBuilder.split(result, positions, ShelfService.MAX_CONTENT_LENGTH);
        if (parts.stream().allMatch(p -> p.text().isBlank())) {
            throw new IllegalArgumentException("Không tìm thấy câu cần lưu");
        }
        String base = result.title() == null ? "Luyện thi" : result.title();
        return parts.stream()
                .filter(p -> !p.text().isBlank())
                .map(p -> shelf.createFromPractice(userId, sessionId,
                        base + " (" + suffix + (p.label() == null ? "" : " · " + p.label()) + ")", p.text()))
                .toList();
    }
}
