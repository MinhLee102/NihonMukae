package com.nihonmukae.backend.admin;

import com.nihonmukae.backend.admin.dto.AdminDtos.BankSummary;
import com.nihonmukae.backend.admin.dto.AdminDtos.EnabledRequest;
import com.nihonmukae.backend.admin.dto.AdminDtos.QuestionDetail;
import com.nihonmukae.backend.admin.dto.AdminDtos.QuestionPage;
import com.nihonmukae.backend.admin.dto.AdminDtos.UpdateQuestionRequest;
import com.nihonmukae.backend.admin.dto.AdminDtos.ServerStatus;
import com.nihonmukae.backend.admin.dto.AdminDtos.TestRow;
import com.nihonmukae.backend.admin.dto.AdminDtos.UserPage;
import com.nihonmukae.backend.admin.dto.AdminDtos.UserRow;
import com.nihonmukae.backend.admin.dto.AdminDtos.UserSummary;
import com.nihonmukae.backend.auth.jwt.JwtUserPrincipal;
import com.nihonmukae.backend.shared.dto.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Toàn bộ /admin/** chỉ ADMIN truy cập được (SecurityConfig)
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;
    private final ServerStatusService serverStatusService;

    public AdminController(AdminService adminService, ServerStatusService serverStatusService) {
        this.adminService = adminService;
        this.serverStatusService = serverStatusService;
    }

    // ─── Kho câu hỏi ────────────────────────────────────────────────────

    @GetMapping("/questions")
    public ApiResponse<QuestionPage> questions(@RequestParam(value = "level", required = false) Short level,
                                               @RequestParam(value = "section", required = false) String section,
                                               @RequestParam(value = "q", required = false) String keyword,
                                               @RequestParam(value = "page", defaultValue = "0") int page,
                                               @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(adminService.listQuestions(level, section, keyword, page, size));
    }

    @GetMapping("/questions/summary")
    public ApiResponse<BankSummary> bankSummary() {
        return ApiResponse.success(adminService.bankSummary());
    }

    @GetMapping("/questions/{id}")
    public ApiResponse<QuestionDetail> question(@PathVariable long id) {
        return ApiResponse.success(adminService.getQuestion(id));
    }

    @PutMapping("/questions/{id}")
    public ApiResponse<QuestionDetail> updateQuestion(@PathVariable long id, @RequestBody UpdateQuestionRequest req) {
        return ApiResponse.success(adminService.updateQuestion(id, req));
    }

    @DeleteMapping("/questions/{id}")
    public ApiResponse<Void> deleteQuestion(@PathVariable long id) {
        adminService.deleteQuestion(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/tests")
    public ApiResponse<List<TestRow>> tests() {
        return ApiResponse.success(adminService.listTests());
    }

    @DeleteMapping("/tests/{id}")
    public ApiResponse<TestRow> deleteTest(@PathVariable long id) {
        return ApiResponse.success(adminService.deleteTest(id));
    }

    @DeleteMapping("/questions/hidden")
    public ApiResponse<Map<String, Integer>> purgeHidden() {
        return ApiResponse.success(Map.of("deleted", adminService.purgeHidden()));
    }

    // ─── Người dùng ─────────────────────────────────────────────────────

    @GetMapping("/users")
    public ApiResponse<UserPage> users(@RequestParam(value = "q", required = false) String keyword,
                                       @RequestParam(value = "status", required = false) String status,
                                       @RequestParam(value = "page", defaultValue = "0") int page,
                                       @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(adminService.listUsers(keyword, status, page, size));
    }

    @GetMapping("/users/summary")
    public ApiResponse<UserSummary> userSummary() {
        return ApiResponse.success(adminService.userSummary());
    }

    @PutMapping("/users/{id}/enabled")
    public ApiResponse<UserRow> setEnabled(@AuthenticationPrincipal JwtUserPrincipal principal,
                                           @PathVariable UUID id, @RequestBody EnabledRequest req) {
        return ApiResponse.success(adminService.setEnabled(principal.getId(), id, req.enabled()));
    }

    // ─── Trạng thái server ──────────────────────────────────────────────

    @GetMapping("/server")
    public ApiResponse<ServerStatus> server() {
        return ApiResponse.success(serverStatusService.snapshot());
    }
}
