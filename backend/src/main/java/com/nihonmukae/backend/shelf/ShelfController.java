package com.nihonmukae.backend.shelf;

import com.nihonmukae.backend.auth.jwt.JwtUserPrincipal;
import com.nihonmukae.backend.shared.dto.ApiResponse;
import com.nihonmukae.backend.shelf.dto.ShelfItemRequest;
import com.nihonmukae.backend.shelf.dto.ShelfItemResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Mọi endpoint dưới /shelf đều yêu cầu JWT (SecurityConfig: anyRequest authenticated)
@RestController
@RequestMapping("/shelf")
public class ShelfController {

    private final ShelfService shelfService;

    public ShelfController(ShelfService shelfService) {
        this.shelfService = shelfService;
    }

    @GetMapping
    public ApiResponse<List<ShelfItemResponse>> list(@AuthenticationPrincipal JwtUserPrincipal principal) {
        return ApiResponse.success(shelfService.list(principal.getId()));
    }

    @PostMapping
    public ApiResponse<ShelfItemResponse> create(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                 @Valid @RequestBody ShelfItemRequest req) {
        return ApiResponse.success(shelfService.create(principal.getId(), req));
    }

    @GetMapping("/{id}")
    public ApiResponse<ShelfItemResponse> get(@AuthenticationPrincipal JwtUserPrincipal principal,
                                              @PathVariable UUID id) {
        return ApiResponse.success(shelfService.get(principal.getId(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ShelfItemResponse> update(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                 @PathVariable UUID id,
                                                 @Valid @RequestBody ShelfItemRequest req) {
        return ApiResponse.success(shelfService.update(principal.getId(), id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal JwtUserPrincipal principal,
                                    @PathVariable UUID id) {
        shelfService.delete(principal.getId(), id);
        return ApiResponse.success(null);
    }
}
