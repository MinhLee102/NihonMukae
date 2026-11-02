package com.nihonmukae.backend.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpRange;
import org.springframework.stereotype.Component;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lưu file vào một thư mục (production: volume Docker, dev: thư mục tạm). Dùng khi chưa cấu hình Google Drive. */
@Component
@ConditionalOnProperty(name = "app.media.provider", havingValue = "local", matchIfMissing = true)
public class LocalDiskMediaStorage implements MediaStorage {

    private final Path dir;

    public LocalDiskMediaStorage(@Value("${app.media.local-dir:}") String localDir) throws IOException {
        String base = localDir == null || localDir.isBlank()
                ? System.getProperty("java.io.tmpdir") + "/nihongo-media" : localDir;
        this.dir = Path.of(base).toAbsolutePath().normalize();
        Files.createDirectories(dir);
    }

    @Override
    public String name() {
        return "local";
    }

    @Override
    public String put(UUID id, InputStream content, long size, String mimeType) throws IOException {
        String key = id.toString();
        // Ghi ra file tạm rồi đổi tên, để không bao giờ có file dang dở được phát ra
        Path tmp = Files.createTempFile(dir, "upload-", ".part");
        try {
            Files.copy(content, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, resolve(key), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
        return key;
    }

    @Override
    public Opened open(String storageKey, long size, String rangeHeader) throws IOException {
        Path file = resolve(storageKey);
        Map<String, String> headers = new LinkedHashMap<>();
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
            if (!ranges.isEmpty()) {
                long start = ranges.get(0).getRangeStart(size);
                long end = ranges.get(0).getRangeEnd(size);
                InputStream in = Files.newInputStream(file);
                in.skipNBytes(start);
                headers.put("Content-Range", "bytes " + start + "-" + end + "/" + size);
                headers.put("Content-Length", String.valueOf(end - start + 1));
                return new Opened(206, headers, new BoundedInputStream(in, end - start + 1));
            }
        }
        headers.put("Content-Length", String.valueOf(size));
        return new Opened(200, headers, Files.newInputStream(file));
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolve(storageKey));
    }

    /** Khóa luôn là UUID do server tạo, nhưng vẫn chặn mọi đường dẫn thoát ra ngoài thư mục lưu. */
    private Path resolve(String key) {
        Path p = dir.resolve(key).normalize();
        if (!p.startsWith(dir) || key.contains("/") || key.contains("\\")) {
            throw new IllegalArgumentException("Khóa file không hợp lệ");
        }
        return p;
    }

    /** Chỉ đọc tối đa {@code limit} byte, để trả đúng đoạn được yêu cầu. */
    private static final class BoundedInputStream extends FilterInputStream {
        private long remaining;

        BoundedInputStream(InputStream in, long limit) {
            super(in);
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int b = super.read();
            if (b >= 0) {
                remaining--;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int n = super.read(b, off, (int) Math.min(len, remaining));
            if (n > 0) {
                remaining -= n;
            }
            return n;
        }
    }
}
