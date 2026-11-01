package com.nihonmukae.backend.question;

import com.nihonmukae.backend.auth.jwt.JwtUserPrincipal;
import com.nihonmukae.backend.question.dto.ImportDtos;
import com.nihonmukae.backend.question.dto.ImportFile;
import com.nihonmukae.backend.question.dto.QuestionTypeResponse;
import com.nihonmukae.backend.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Mọi endpoint yêu cầu JWT (SecurityConfig: anyRequest authenticated)
@RestController
public class QuestionController {

    private final QuestionService questionService;
    private final QuestionImportService importService;

    public QuestionController(QuestionService questionService, QuestionImportService importService) {
        this.questionService = questionService;
        this.importService = importService;
    }

    // GET /question-types?level=3 — dạng bài áp dụng cho level, kèm số câu đang có trong kho
    @GetMapping("/question-types")
    public ApiResponse<List<QuestionTypeResponse>> types(@RequestParam("level") short level) {
        return ApiResponse.success(questionService.listTypes(level));
    }

    @PostMapping("/questions/import/preview")
    public ApiResponse<ImportDtos.PreviewResponse> preview(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                           @RequestBody ImportFile file) {
        return ApiResponse.success(importService.preview(principal.getId(), file));
    }

    @PostMapping("/questions/import/commit")
    public ApiResponse<ImportDtos.CommitResponse> commit(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                         @Valid @RequestBody ImportDtos.CommitRequest req) {
        boolean admin = "ADMIN".equals(principal.getRole());
        return ApiResponse.success(importService.commit(principal.getId(), admin, req.previewId(), req.onDuplicate()));
    }
}
