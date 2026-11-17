package com.nihonmukae.backend.shadowing;

import com.nihonmukae.backend.auth.jwt.JwtUserPrincipal;
import com.nihonmukae.backend.shadowing.ShadowingDtos.MarksRequest;
import com.nihonmukae.backend.shadowing.ShadowingDtos.MaterialDetail;
import com.nihonmukae.backend.shadowing.ShadowingDtos.MaterialRequest;
import com.nihonmukae.backend.shadowing.ShadowingDtos.MaterialSummary;
import com.nihonmukae.backend.shared.dto.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Mọi endpoint dưới /shadowing yêu cầu JWT (SecurityConfig: anyRequest authenticated)
@RestController
@RequestMapping("/shadowing/materials")
public class ShadowingController {

    private final ShadowingService service;

    public ShadowingController(ShadowingService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<MaterialSummary>> list(@AuthenticationPrincipal JwtUserPrincipal principal) {
        return ApiResponse.success(service.list(principal.getId()));
    }

    @PostMapping
    public ApiResponse<MaterialDetail> create(@AuthenticationPrincipal JwtUserPrincipal principal,
                                              @RequestBody MaterialRequest req) {
        return ApiResponse.success(service.create(principal.getId(), req));
    }

    @GetMapping("/{id}")
    public ApiResponse<MaterialDetail> get(@AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable UUID id) {
        return ApiResponse.success(service.get(principal.getId(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<MaterialDetail> update(@AuthenticationPrincipal JwtUserPrincipal principal,
                                              @PathVariable UUID id, @RequestBody MaterialRequest req) {
        return ApiResponse.success(service.update(principal.getId(), id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable UUID id) {
        service.delete(principal.getId(), id);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/marks")
    public ApiResponse<List<Integer>> saveMarks(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                @PathVariable UUID id, @RequestBody MarksRequest req) {
        return ApiResponse.success(service.saveMarks(principal.getId(), id, req));
    }
}
