package com.nihonmukae.backend.media.dto;

import java.util.UUID;

/** Kết quả upload. url là đường dẫn tương đối (/media/{id}), được ghi vào trường audio_url hoặc [[img:...]] của đề. */
public record MediaInfo(UUID id, String url, String kind, String mimeType, long sizeBytes, String originalName,
                        boolean duplicate) {
}
