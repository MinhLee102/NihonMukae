package com.nihonmukae.backend.feedback;

import com.nihonmukae.backend.auth.jwt.JwtUserPrincipal;
import com.nihonmukae.backend.feedback.FeedbackDtos.CreateRequest;
import com.nihonmukae.backend.feedback.FeedbackDtos.Page;
import com.nihonmukae.backend.feedback.FeedbackDtos.Summary;
import com.nihonmukae.backend.feedback.FeedbackDtos.UpdateRequest;
import com.nihonmukae.backend.shared.dto.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class FeedbackController {

    private final FeedbackService service;

    public FeedbackController(FeedbackService service) {
        this.service = service;
    }

    // Người dùng đã đăng nhập
    @PostMapping("/feedback")
    public ApiResponse<Map<String, Long>> create(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                 @RequestBody CreateRequest req) {
        return ApiResponse.success(Map.of("id", service.create(principal.getId(), req)));
    }

    // Duyệt góp ý: ADMIN và MODERATOR (SecurityConfig)
    @GetMapping("/admin/feedback")
    public ApiResponse<Page> list(@RequestParam(value = "status", required = false) String status,
                                  @RequestParam(value = "category", required = false) String category,
                                  @RequestParam(value = "page", defaultValue = "0") int page,
                                  @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(service.list(status, category, page, size));
    }

    @GetMapping("/admin/feedback/summary")
    public ApiResponse<Summary> summary() {
        return ApiResponse.success(service.summary());
    }

    @PutMapping("/admin/feedback/{id}")
    public ApiResponse<Void> update(@PathVariable long id, @RequestBody UpdateRequest req) {
        service.update(id, req);
        return ApiResponse.success(null);
    }
}
