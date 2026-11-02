package com.nihonmukae.backend.media;

import com.nihonmukae.backend.media.MediaStorage.Opened;
import com.nihonmukae.backend.media.MediaValidator.Kind;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaTest {

    private static byte[] pad(byte[] start) {
        byte[] out = new byte[16];
        System.arraycopy(start, 0, out, 0, Math.min(start.length, 16));
        return out;
    }

    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            b[i] = (byte) values[i];
        }
        return b;
    }

    // ─── Nhận diện định dạng ────────────────────────────────────────────

    @Test
    void acceptsTheFormatsWeNeed() {
        assertEquals("audio/mpeg", MediaValidator.detect(pad("ID3".getBytes(StandardCharsets.US_ASCII)), 16).mimeType());
        assertEquals("audio/mpeg", MediaValidator.detect(pad(bytes(0xFF, 0xFB, 0x90)), 16).mimeType());
        assertEquals("audio/ogg", MediaValidator.detect(pad("OggS".getBytes(StandardCharsets.US_ASCII)), 16).mimeType());
        assertEquals("audio/wav", MediaValidator.detect("RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.ISO_8859_1), 16).mimeType());
        assertEquals("audio/mp4", MediaValidator.detect("\0\0\0\u0020ftypM4A ".getBytes(StandardCharsets.ISO_8859_1), 16).mimeType());
        assertEquals(Kind.IMAGE, MediaValidator.detect(pad(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)), 16).kind());
        assertEquals("image/jpeg", MediaValidator.detect(pad(bytes(0xFF, 0xD8, 0xFF, 0xE0)), 16).mimeType());
        assertEquals("image/webp", MediaValidator.detect("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1), 16).mimeType());
    }

    @Test
    void rejectsEverythingElse() {
        // SVG, HTML, script, exe và file rỗng đều bị từ chối dù đặt tên có đuôi .mp3
        for (String content : new String[]{"<svg xmlns=", "<!DOCTYPE html>", "<?php echo 1;", "#!/bin/sh\necho", "MZ\u0090\0\u0003\0\0\0"}) {
            byte[] h = pad(content.getBytes(StandardCharsets.ISO_8859_1));
            assertThrows(IllegalArgumentException.class, () -> MediaValidator.detect(h, 16), content);
        }
        assertThrows(IllegalArgumentException.class, () -> MediaValidator.detect(new byte[16], 0));
    }

    // ─── Lưu trữ local và Range ─────────────────────────────────────────

    @Test
    void localStorageServesWholeFileAndRanges(@TempDir Path dir) throws Exception {
        LocalDiskMediaStorage storage = new LocalDiskMediaStorage(dir.toString());
        byte[] data = "0123456789abcdefghij".getBytes(StandardCharsets.US_ASCII);
        UUID id = UUID.randomUUID();
        String key = storage.put(id, new ByteArrayInputStream(data), data.length, "audio/mpeg");

        Opened whole = storage.open(key, data.length, null);
        assertEquals(200, whole.status());
        assertArrayEquals(data, whole.body().readAllBytes());

        Opened part = storage.open(key, data.length, "bytes=5-9");
        assertEquals(206, part.status());
        assertEquals("bytes 5-9/20", part.headers().get("Content-Range"));
        assertEquals("56789", new String(part.body().readAllBytes(), StandardCharsets.US_ASCII));

        Opened tail = storage.open(key, data.length, "bytes=15-");
        assertEquals("fghij", new String(tail.body().readAllBytes(), StandardCharsets.US_ASCII));

        storage.delete(key);
        assertThrows(java.io.IOException.class, () -> storage.open(key, data.length, null).body().read());
    }

    @Test
    void localStorageRefusesPathTraversal(@TempDir Path dir) throws Exception {
        LocalDiskMediaStorage storage = new LocalDiskMediaStorage(dir.toString());
        assertThrows(IllegalArgumentException.class, () -> storage.open("../secret", 1, null));
        assertThrows(IllegalArgumentException.class, () -> storage.open("a/b", 1, null));
    }

    @Test
    void fileNamesAreCleaned() {
        assertEquals("track01.mp3", MediaService.cleanName("C:\\Users\\me\\track01.mp3"));
        assertEquals("a.mp3", MediaService.cleanName("../../etc/a.mp3"));
        assertEquals("x", MediaService.cleanName("x\u0000\u0007"));
    }

    // ─── Google Drive với server giả ────────────────────────────────────

    private HttpServer server;
    private final AtomicInteger tokenCalls = new AtomicInteger();
    private final AtomicInteger folderCreates = new AtomicInteger();
    private volatile String lastUploadBody;
    private volatile String lastUploadAuth;
    private volatile String lastRange;
    private static final byte[] DRIVE_FILE = "ABCDEFGHIJKLMNOPQRST".getBytes(StandardCharsets.US_ASCII);

    @BeforeEach
    void startFakeGoogle() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", ex -> {
            tokenCalls.incrementAndGet();
            byte[] body = "{\"access_token\":\"tok-1\",\"expires_in\":3600}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/drive/v3/files", ex -> {
            String method = ex.getRequestMethod();
            String query = ex.getRequestURI().getRawQuery();
            if (method.equals("GET") && ex.getRequestURI().getPath().equals("/drive/v3/files") && query.startsWith("fields=files")) {
                respond(ex, 200, "{\"files\":[]}");
            } else if (method.equals("POST")) {
                folderCreates.incrementAndGet();
                respond(ex, 200, "{\"id\":\"folder-1\"}");
            } else if (method.equals("GET")) {
                lastRange = ex.getRequestHeaders().getFirst("Range");
                if (!"Bearer tok-1".equals(ex.getRequestHeaders().getFirst("Authorization"))) {
                    respond(ex, 401, "{}");
                    return;
                }
                if (lastRange != null) {
                    ex.getResponseHeaders().add("Content-Range", "bytes 2-5/20");
                    ex.sendResponseHeaders(206, 4);
                    ex.getResponseBody().write(DRIVE_FILE, 2, 4);
                } else {
                    ex.sendResponseHeaders(200, DRIVE_FILE.length);
                    ex.getResponseBody().write(DRIVE_FILE);
                }
                ex.close();
            } else if (method.equals("DELETE")) {
                ex.sendResponseHeaders(204, -1);
                ex.close();
            }
        });
        server.createContext("/upload/drive/v3/files", ex -> {
            lastUploadAuth = ex.getRequestHeaders().getFirst("Authorization");
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            ex.getRequestBody().transferTo(buf);
            lastUploadBody = buf.toString(StandardCharsets.ISO_8859_1);
            respond(ex, 200, "{\"id\":\"drive-file-9\"}");
        });
        server.start();
    }

    @AfterEach
    void stopFakeGoogle() {
        server.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange ex, int code, String json) throws java.io.IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, body.length);
        ex.getResponseBody().write(body);
        ex.close();
    }

    private GoogleDriveMediaStorage drive() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new GoogleDriveMediaStorage("cid", "secret", "refresh", "", base, base + "/token");
    }

    @Test
    void driveUploadCreatesAppFolderAndSendsMultipart() throws Exception {
        GoogleDriveMediaStorage storage = drive();
        byte[] content = "FAKE-MP3-BYTES".getBytes(StandardCharsets.US_ASCII);
        UUID id = UUID.randomUUID();

        String key = storage.put(id, new ByteArrayInputStream(content), content.length, "audio/mpeg");

        assertEquals("drive-file-9", key);
        assertEquals("Bearer tok-1", lastUploadAuth);
        assertEquals(1, folderCreates.get(), "thư mục do app tạo ở lần đầu");
        assertTrue(lastUploadBody.contains("\"name\":\"" + id + "\""));
        assertTrue(lastUploadBody.contains("\"parents\":[\"folder-1\"]"));
        assertTrue(lastUploadBody.contains("FAKE-MP3-BYTES"));
        assertTrue(lastUploadBody.contains("Content-Type: audio/mpeg"));

        storage.put(UUID.randomUUID(), new ByteArrayInputStream(content), content.length, "audio/mpeg");
        assertEquals(1, folderCreates.get(), "thư mục được nhớ, không tạo lại");
        assertEquals(1, tokenCalls.get(), "access token được dùng lại");
    }

    @Test
    void driveDownloadForwardsRange() throws Exception {
        GoogleDriveMediaStorage storage = drive();

        Opened whole = storage.open("drive-file-9", DRIVE_FILE.length, null);
        assertEquals(200, whole.status());
        assertArrayEquals(DRIVE_FILE, whole.body().readAllBytes());

        Opened part = storage.open("drive-file-9", DRIVE_FILE.length, "bytes=2-5");
        assertEquals("bytes=2-5", lastRange);
        assertEquals(206, part.status());
        assertEquals("bytes 2-5/20", part.headers().get("Content-Range"));
        assertEquals("CDEF", new String(part.body().readAllBytes(), StandardCharsets.US_ASCII));

        storage.delete("drive-file-9");
    }

    @Test
    void driveStorageRefusesToStartWithoutCredentials() {
        assertThrows(IllegalStateException.class,
                () -> new GoogleDriveMediaStorage("", "", "", "", "http://x", "http://x/token"));
    }
}
