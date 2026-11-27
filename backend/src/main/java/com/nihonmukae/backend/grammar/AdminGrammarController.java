package com.nihonmukae.backend.grammar;

import com.nihonmukae.backend.grammar.AdminGrammarService.BatchItem;
import com.nihonmukae.backend.grammar.AdminGrammarService.ContentResult;
import com.nihonmukae.backend.grammar.AdminGrammarService.DuplicateRow;
import com.nihonmukae.backend.grammar.AdminGrammarService.IdsRequest;
import com.nihonmukae.backend.grammar.AdminGrammarService.MergeRequest;
import com.nihonmukae.backend.grammar.AdminGrammarService.LevelSummary;
import com.nihonmukae.backend.grammar.AdminGrammarService.ListImport;
import com.nihonmukae.backend.grammar.AdminGrammarService.ListResult;
import com.nihonmukae.backend.grammar.AdminGrammarService.PointRow;
import com.nihonmukae.backend.shared.dto.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Soạn ngữ pháp trên trang Admin. Chỉ ADMIN (SecurityConfig: /admin/**). */
@RestController
@RequestMapping("/admin/grammar")
public class AdminGrammarController {

    private final AdminGrammarService service;

    public AdminGrammarController(AdminGrammarService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ApiResponse<List<LevelSummary>> summary() {
        return ApiResponse.success(service.summary());
    }

    @GetMapping("/points")
    public ApiResponse<List<PointRow>> points(@RequestParam("level") int level) {
        return ApiResponse.success(service.points(level));
    }

    // Bước 1: danh sách tên mẫu (câu trả lời của AI cho prompt liệt kê hoặc trích từ file)
    @PostMapping("/list")
    public ApiResponse<ListResult> importList(@RequestBody ListImport req) {
        return ApiResponse.success(service.importList(req));
    }

    // Bước 2: các mẫu kế tiếp chưa có nội dung, để dựng prompt
    @GetMapping("/batch")
    public ApiResponse<List<BatchItem>> batch(@RequestParam("level") int level,
                                              @RequestParam(value = "size", required = false) Integer size) {
        return ApiResponse.success(service.nextBatch(level, size));
    }

    // Bước 2: câu trả lời của AI (mảng JSON), kiểm tra rồi lưu
    @PostMapping("/content")
    public ApiResponse<ContentResult> content(@RequestBody List<Map<String, Object>> entries) {
        return ApiResponse.success(service.saveContent(entries));
    }

    // Mọi mẫu chờ viết (level bỏ trống là mọi cấp), để tải file giao cho agent
    @GetMapping("/pending")
    public ApiResponse<List<BatchItem>> pending(@RequestParam(value = "level", required = false) Integer level) {
        return ApiResponse.success(service.allPending(level));
    }

    @GetMapping("/duplicates")
    public ApiResponse<List<List<DuplicateRow>>> duplicates() {
        return ApiResponse.success(service.duplicates());
    }

    @PostMapping("/merge")
    public ApiResponse<Void> merge(@RequestBody MergeRequest req) {
        service.merge(req);
        return ApiResponse.success(null);
    }

    @PostMapping("/distinct")
    public ApiResponse<Void> distinct(@RequestBody IdsRequest req) {
        service.markDistinct(req);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/points/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    @PutMapping("/points/{id}/restore")
    public ApiResponse<Void> restore(@PathVariable long id) {
        service.restore(id);
        return ApiResponse.success(null);
    }
}
