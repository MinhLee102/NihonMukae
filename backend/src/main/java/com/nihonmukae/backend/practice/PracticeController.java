package com.nihonmukae.backend.practice;

import com.nihonmukae.backend.auth.jwt.JwtUserPrincipal;
import com.nihonmukae.backend.practice.dto.ResultDtos.Result;
import com.nihonmukae.backend.practice.dto.SessionDtos.AnswerRequest;
import com.nihonmukae.backend.practice.dto.SessionDtos.AnswerResult;
import com.nihonmukae.backend.practice.dto.SessionDtos.SessionSummary;
import com.nihonmukae.backend.practice.dto.SessionDtos.SessionView;
import com.nihonmukae.backend.practice.dto.SessionDtos.StartRequest;
import com.nihonmukae.backend.shared.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

// Mọi endpoint yêu cầu JWT (SecurityConfig: anyRequest authenticated)
@RestController
@RequestMapping("/practice")
public class PracticeController {

    private final PracticeSessionService sessionService;

    public PracticeController(PracticeSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/sessions")
    public ApiResponse<SessionView> create(@AuthenticationPrincipal JwtUserPrincipal principal,
                                           @Valid @RequestBody StartRequest req) {
        return ApiResponse.success(sessionService.create(principal.getId(), req));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<SessionSummary>> list(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                  @RequestParam(value = "mode", required = false) String mode) {
        return ApiResponse.success(sessionService.list(principal.getId(), mode));
    }

    @GetMapping("/sessions/{id}")
    public ApiResponse<SessionView> get(@AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable UUID id) {
        return ApiResponse.success(sessionService.get(principal.getId(), id));
    }

    @PutMapping("/sessions/{id}/answers/{questionId}")
    public ApiResponse<AnswerResult> answer(@AuthenticationPrincipal JwtUserPrincipal principal,
                                            @PathVariable UUID id, @PathVariable long questionId,
                                            @RequestBody AnswerRequest req) {
        return ApiResponse.success(sessionService.answer(principal.getId(), id, questionId, req.selected()));
    }

    @PostMapping("/sessions/{id}/blocks/{blockNo}/start")
    public ApiResponse<SessionView> startBlock(@AuthenticationPrincipal JwtUserPrincipal principal,
                                               @PathVariable UUID id, @PathVariable int blockNo) {
        return ApiResponse.success(sessionService.startBlock(principal.getId(), id, blockNo));
    }

    @PostMapping("/sessions/{id}/blocks/{blockNo}/submit")
    public ApiResponse<SessionView> submitBlock(@AuthenticationPrincipal JwtUserPrincipal principal,
                                                @PathVariable UUID id, @PathVariable int blockNo) {
        return ApiResponse.success(sessionService.submitBlock(principal.getId(), id, blockNo));
    }

    @PostMapping("/sessions/{id}/submit")
    public ApiResponse<Result> submit(@AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable UUID id) {
        return ApiResponse.success(sessionService.submit(principal.getId(), id));
    }

    @PostMapping("/sessions/{id}/retry")
    public ApiResponse<SessionView> retry(@AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable UUID id) {
        return ApiResponse.success(sessionService.retry(principal.getId(), id));
    }

    @GetMapping("/sessions/{id}/result")
    public ApiResponse<Result> result(@AuthenticationPrincipal JwtUserPrincipal principal, @PathVariable UUID id) {
        return ApiResponse.success(sessionService.result(principal.getId(), id));
    }
}
