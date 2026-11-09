package com.nihonmukae.backend.admin;

import com.nihonmukae.backend.auth.jwt.JwtUserPrincipal;
import com.nihonmukae.backend.dictionary.dto.DictDtos.AdminContributionPage;
import com.nihonmukae.backend.dictionary.dto.DictDtos.AdminEntryDetail;
import com.nihonmukae.backend.dictionary.dto.DictDtos.AdminEntryPage;
import com.nihonmukae.backend.dictionary.dto.DictDtos.AdminSummary;
import com.nihonmukae.backend.dictionary.dto.DictDtos.EntryRequest;
import com.nihonmukae.backend.dictionary.dto.DictDtos.ReviewRequest;
import com.nihonmukae.backend.shared.dto.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// /admin/dictionary/** dành cho ADMIN và MODERATOR (SecurityConfig)
@RestController
@RequestMapping("/admin/dictionary")
public class AdminDictionaryController {

    private final AdminDictionaryService service;

    public AdminDictionaryController(AdminDictionaryService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ApiResponse<AdminSummary> summary() {
        return ApiResponse.success(service.summary());
    }

    @GetMapping("/entries")
    public ApiResponse<AdminEntryPage> entries(@RequestParam(value = "q", required = false) String keyword,
                                               @RequestParam(value = "filter", required = false) String filter,
                                               @RequestParam(value = "page", defaultValue = "0") int page,
                                               @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(service.listEntries(keyword, filter, page, size));
    }

    @GetMapping("/entries/{id}")
    public ApiResponse<AdminEntryDetail> entry(@PathVariable long id) {
        return ApiResponse.success(service.getEntry(id));
    }

    @PostMapping("/entries")
    public ApiResponse<AdminEntryDetail> create(@RequestBody EntryRequest req) {
        return ApiResponse.success(service.create(req), "Đã thêm mục từ");
    }

    @PutMapping("/entries/{id}")
    public ApiResponse<AdminEntryDetail> update(@PathVariable long id, @RequestBody EntryRequest req) {
        return ApiResponse.success(service.update(id, req), "Đã lưu");
    }

    @DeleteMapping("/entries/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ApiResponse.success(null, "Đã xoá mục từ");
    }

    // ─── Đóng góp của người dùng ────────────────────────────────────────

    @GetMapping("/contributions")
    public ApiResponse<AdminContributionPage> contributions(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(service.listContributions(status, page, size));
    }

    @PutMapping("/contributions/{id}")
    public ApiResponse<Void> review(@AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable long id,
                                    @RequestBody ReviewRequest req) {
        service.review(principal.getId(), id, req);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/contributions/{id}")
    public ApiResponse<Void> deleteContribution(@PathVariable long id) {
        service.deleteContribution(id);
        return ApiResponse.success(null);
    }
}
