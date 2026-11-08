package com.nihonmukae.backend.practice;

import com.nihonmukae.backend.auth.jwt.JwtUserPrincipal;
import com.nihonmukae.backend.shared.dto.ApiResponse;
import com.nihonmukae.backend.shelf.dto.ShelfItemResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/practice")
public class PracticeShelfController {

    private final PracticeShelfService service;

    public PracticeShelfController(PracticeShelfService service) {
        this.service = service;
    }

    /** Lưu câu của một phiên đã nộp vào My Shelf: scope = wrong | all | question (kèm position). Bài dài thành nhiều mục. */
    @PostMapping("/sessions/{id}/save-to-shelf")
    public ApiResponse<List<ShelfItemResponse>> save(@AuthenticationPrincipal JwtUserPrincipal principal,
                                               @PathVariable UUID id, @RequestBody PracticeShelfService.SaveRequest req) {
        return ApiResponse.success(service.save(principal.getId(), id, req));
    }
}
