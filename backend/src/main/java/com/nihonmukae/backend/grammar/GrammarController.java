package com.nihonmukae.backend.grammar;

import com.nihonmukae.backend.grammar.GrammarService.Detail;
import com.nihonmukae.backend.grammar.GrammarService.DetectRequest;
import com.nihonmukae.backend.grammar.GrammarService.Found;
import com.nihonmukae.backend.grammar.GrammarService.Summary;
import com.nihonmukae.backend.shared.dto.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Ngữ pháp cho người học (công khai, chỉ đọc). */
@RestController
@RequestMapping("/grammar")
public class GrammarController {

    private final GrammarService grammarService;

    public GrammarController(GrammarService grammarService) {
        this.grammarService = grammarService;
    }

    // GET /grammar/search?q=ざるを得ない&level=1 : theo tên mẫu, cách đọc (kana, romaji) hoặc nghĩa tiếng Việt
    @GetMapping("/search")
    public ApiResponse<List<Summary>> search(@RequestParam String q, @RequestParam(required = false) Integer level) {
        return ApiResponse.success(grammarService.search(q, level));
    }

    // GET /grammar/levels/3 : mọi mẫu của N3
    @GetMapping("/levels/{level}")
    public ApiResponse<List<Summary>> byLevel(@PathVariable int level) {
        return ApiResponse.success(grammarService.byLevel(level));
    }

    @GetMapping("/{id}")
    public ApiResponse<Detail> detail(@PathVariable long id) {
        return ApiResponse.success(grammarService.detail(id));
    }

    // POST /grammar/detect {"text": "雨が強いので、試合を中止せざるを得ない。"} : các mẫu có trong câu
    @PostMapping("/detect")
    public ApiResponse<List<Found>> detect(@RequestBody DetectRequest req) {
        return ApiResponse.success(grammarService.detect(req == null ? null : req.text()));
    }
}
