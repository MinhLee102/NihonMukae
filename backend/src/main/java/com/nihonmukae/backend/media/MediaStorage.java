package com.nihonmukae.backend.media;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

/** Nơi lưu file thật. Hai cách: thư mục trên ổ đĩa (local) hoặc Google Drive của admin (drive). */
public interface MediaStorage {

    /** Nội dung trả cho trình duyệt: mã 200 (cả file) hoặc 206 (một đoạn khi tua audio), kèm header cần thiết. */
    record Opened(int status, Map<String, String> headers, InputStream body) {
    }

    /** Tên lưu vào media_files.storage: "local" hoặc "drive". */
    String name();

    /**
     * Lưu file và trả về storageKey (tên file local, hoặc id file trên Drive).
     *
     * @param id   id của bản ghi media_files, dùng làm tên file
     * @param size số byte của nội dung
     */
    String put(UUID id, InputStream content, long size, String mimeType) throws IOException;

    /** Mở file, hỗ trợ header Range (rangeHeader có thể null). */
    Opened open(String storageKey, long size, String rangeHeader) throws IOException;

    void delete(String storageKey) throws IOException;
}
