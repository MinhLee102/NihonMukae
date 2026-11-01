package com.nihonmukae.backend.question;

import com.nihonmukae.backend.question.dto.JlptDtos.Blueprint;
import com.nihonmukae.backend.question.dto.JlptDtos.TestSummary;
import com.nihonmukae.backend.shared.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Mọi endpoint yêu cầu JWT (SecurityConfig: anyRequest authenticated)
@RestController
@RequestMapping("/jlpt")
public class JlptController {

    private final JlptService jlptService;

    public JlptController(JlptService jlptService) {
        this.jlptService = jlptService;
    }

    // GET /jlpt/tests?level=3 — các đề thi đã có câu hỏi
    @GetMapping("/tests")
    public ApiResponse<List<TestSummary>> tests(@RequestParam("level") short level) {
        return ApiResponse.success(jlptService.listTests(level));
    }

    // GET /jlpt/blueprint?level=3 — cấu trúc đề thật, kèm cảnh báo kho thiếu câu
    @GetMapping("/blueprint")
    public ApiResponse<Blueprint> blueprint(@RequestParam("level") short level) {
        return ApiResponse.success(jlptService.blueprint(level));
    }
}
