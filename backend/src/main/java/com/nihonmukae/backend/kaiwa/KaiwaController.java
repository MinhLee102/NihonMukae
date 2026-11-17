package com.nihonmukae.backend.kaiwa;

import com.nihonmukae.backend.auth.jwt.JwtUserPrincipal;
import com.nihonmukae.backend.kaiwa.KaiwaService.SaveRequest;
import com.nihonmukae.backend.kaiwa.KaiwaService.SessionDto;
import com.nihonmukae.backend.shared.dto.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Server chỉ lưu tóm tắt. Hội thoại với Gemini diễn ra trong trình duyệt bằng key của chính user.
@RestController
@RequestMapping("/kaiwa/sessions")
public class KaiwaController {

    private final KaiwaService service;

    public KaiwaController(KaiwaService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<SessionDto>> list(@AuthenticationPrincipal JwtUserPrincipal principal) {
        return ApiResponse.success(service.list(principal.getId()));
    }

    @PostMapping
    public ApiResponse<SessionDto> save(@AuthenticationPrincipal JwtUserPrincipal principal, @RequestBody SaveRequest req) {
        return ApiResponse.success(service.save(principal.getId(), req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable UUID id) {
        service.delete(principal.getId(), id);
        return ApiResponse.success(null);
    }
}
