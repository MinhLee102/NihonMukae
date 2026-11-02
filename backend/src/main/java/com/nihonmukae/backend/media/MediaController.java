package com.nihonmukae.backend.media;

import com.nihonmukae.backend.auth.jwt.JwtUserPrincipal;
import com.nihonmukae.backend.media.MediaService.Playback;
import com.nihonmukae.backend.media.dto.MediaInfo;
import com.nihonmukae.backend.shared.dto.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    // Chỉ ADMIN (SecurityConfig: /admin/**)
    @PostMapping("/admin/media")
    public ApiResponse<MediaInfo> upload(@AuthenticationPrincipal JwtUserPrincipal principal,
                                         @RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.success(mediaService.upload(principal.getId(), file));
    }

    // Công khai (thẻ audio và img không gửi được token). Địa chỉ chứa UUID ngẫu nhiên, không đoán được.
    @GetMapping("/media/{id}")
    public ResponseEntity<StreamingResponseBody> play(@PathVariable UUID id,
                                                      @RequestHeader(value = HttpHeaders.RANGE, required = false) String range)
            throws IOException {
        Playback playback;
        try {
            playback = mediaService.open(id, range);
        } catch (IllegalArgumentException invalidRange) {
            playback = mediaService.open(id, null);
        }

        var builder = ResponseEntity.status(playback.opened().status())
                .contentType(MediaType.parseMediaType(playback.file().getMimeType()))
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline");
        playback.opened().headers().forEach(builder::header);

        InputStream body = playback.opened().body();
        return builder.body(out -> {
            try (body) {
                body.transferTo(out);
            }
        });
    }
}
