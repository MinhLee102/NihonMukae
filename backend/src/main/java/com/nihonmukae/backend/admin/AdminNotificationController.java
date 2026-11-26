package com.nihonmukae.backend.admin;

import com.nihonmukae.backend.admin.AdminNotificationService.Notifications;
import com.nihonmukae.backend.auth.jwt.JwtUserPrincipal;
import com.nihonmukae.backend.shared.dto.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminNotificationController {

    private final AdminNotificationService notificationService;

    public AdminNotificationController(AdminNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // ADMIN và MODERATOR (SecurityConfig). Tài khoản chờ duyệt chỉ ADMIN thấy, vì chỉ ADMIN duyệt được.
    @GetMapping("/admin/notifications")
    public ApiResponse<Notifications> notifications(@AuthenticationPrincipal JwtUserPrincipal principal) {
        boolean admin = principal.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return ApiResponse.success(notificationService.get(admin));
    }
}
