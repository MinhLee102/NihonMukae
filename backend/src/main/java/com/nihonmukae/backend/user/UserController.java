package com.nihonmukae.backend.user;

import com.nihonmukae.backend.auth.jwt.JwtUserPrincipal;
import com.nihonmukae.backend.shared.dto.ApiResponse;
import com.nihonmukae.backend.user.dto.ProfileDtos.Profile;
import com.nihonmukae.backend.user.dto.ProfileDtos.UpdateRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Yêu cầu JWT (SecurityConfig: anyRequest authenticated)
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<Profile> me(@AuthenticationPrincipal JwtUserPrincipal principal) {
        return ApiResponse.success(userService.getProfile(principal.getId()));
    }

    @PutMapping("/me")
    public ApiResponse<Profile> update(@AuthenticationPrincipal JwtUserPrincipal principal,
                                       @Valid @RequestBody UpdateRequest req) {
        return ApiResponse.success(userService.updateProfile(principal.getId(), req));
    }
}
