package com.nihonmukae.backend.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lưu file vào Google Drive của admin, dưới quyền của chính tài khoản đó (OAuth, refresh token).
 *
 * <p>Dùng scope hẹp {@code drive.file}: ứng dụng chỉ thấy và ghi được các file/thư mục do chính nó tạo, không đọc
 * được phần còn lại của Drive. Vì vậy thư mục chứa file phải do ứng dụng tự tạo (tên cố định bên dưới), không
 * dùng được thư mục có sẵn. File vẫn ở trạng thái riêng tư: người học nghe qua {@code GET /media/{id}} của
 * backend, backend tải từ Drive bằng token rồi chuyển tiếp (kèm Range để tua được).
 */
@Component
@ConditionalOnProperty(name = "app.media.provider", havingValue = "drive")
public class GoogleDriveMediaStorage implements MediaStorage {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveMediaStorage.class);
    private static final String FOLDER_NAME = "NihonMukae media";
    private static final String FOLDER_MIME = "application/vnd.google-apps.folder";

    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private final String apiBase;
    private final String tokenUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile String configuredFolderId;
    private volatile String accessToken;
    private volatile long accessTokenExpiresAtMs;

    public GoogleDriveMediaStorage(@Value("${app.media.drive.client-id:}") String clientId,
                                   @Value("${app.media.drive.client-secret:}") String clientSecret,
                                   @Value("${app.media.drive.refresh-token:}") String refreshToken,
                                   @Value("${app.media.drive.folder-id:}") String folderId,
                                   @Value("${app.media.drive.api-base:https://www.googleapis.com}") String apiBase,
                                   @Value("${app.media.drive.token-url:https://oauth2.googleapis.com/token}") String tokenUrl) {
        if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank()) {
            throw new IllegalStateException("app.media.provider=drive nhưng thiếu GOOGLE_DRIVE_CLIENT_ID, "
                    + "GOOGLE_DRIVE_CLIENT_SECRET hoặc GOOGLE_DRIVE_REFRESH_TOKEN");
        }
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
        this.configuredFolderId = folderId.isBlank() ? null : folderId;
        this.apiBase = apiBase;
        this.tokenUrl = tokenUrl;
    }

    @Override
    public String name() {
        return "drive";
    }

    @Override
    public String put(UUID id, InputStream content, long size, String mimeType) throws IOException {
        String folder = folderId();
        String boundary = "nihongo-" + UUID.randomUUID();
        String metadata = mapper.writeValueAsString(Map.of(
                "name", id.toString(), "mimeType", mimeType, "parents", java.util.List.of(folder)));
        byte[] head = ("--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + metadata
                + "\r\n--" + boundary + "\r\nContent-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--").getBytes(StandardCharsets.UTF_8);

        // Drive cần biết trước Content-Length của request upload nên khai báo tổng độ dài, nội dung vẫn được stream
        var body = HttpRequest.BodyPublishers.concat(
                HttpRequest.BodyPublishers.ofByteArray(head),
                HttpRequest.BodyPublishers.ofInputStream(() -> content),
                HttpRequest.BodyPublishers.ofByteArray(tail));
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase + "/upload/drive/v3/files?uploadType=multipart&fields=id"))
                .header("Authorization", "Bearer " + token())
                .header("Content-Type", "multipart/related; boundary=" + boundary)
                .timeout(Duration.ofMinutes(10))
                .POST(HttpRequest.BodyPublishers.fromPublisher(body, head.length + size + tail.length))
                .build();
        HttpResponse<String> response = send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Google Drive từ chối upload (mã " + response.statusCode() + "): " + abbreviate(response.body()));
        }
        String fileId = mapper.readTree(response.body()).path("id").asText();
        if (fileId.isEmpty()) {
            throw new IOException("Google Drive không trả về id file");
        }
        return fileId;
    }

    @Override
    public Opened open(String storageKey, long size, String rangeHeader) throws IOException {
        HttpResponse<InputStream> response = download(storageKey, rangeHeader, false);
        if (response.statusCode() == 401) {
            response.body().close();
            response = download(storageKey, rangeHeader, true);
        }
        int status = response.statusCode();
        if (status != 200 && status != 206) {
            response.body().close();
            throw new IOException("Google Drive trả mã " + status + " khi tải file");
        }
        Map<String, String> headers = new LinkedHashMap<>();
        response.headers().firstValue("Content-Length").ifPresent(v -> headers.put("Content-Length", v));
        response.headers().firstValue("Content-Range").ifPresent(v -> headers.put("Content-Range", v));
        return new Opened(status, headers, response.body());
    }

    @Override
    public void delete(String storageKey) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase + "/drive/v3/files/" + encode(storageKey)))
                .header("Authorization", "Bearer " + token())
                .timeout(Duration.ofSeconds(30))
                .DELETE().build();
        HttpResponse<String> response = send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2 && response.statusCode() != 404) {
            throw new IOException("Không xóa được file trên Google Drive (mã " + response.statusCode() + ")");
        }
    }

    // ─── Nội bộ ─────────────────────────────────────────────────────────

    private HttpResponse<InputStream> download(String fileId, String rangeHeader, boolean forceRefresh) throws IOException {
        if (forceRefresh) {
            accessToken = null;
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(apiBase + "/drive/v3/files/" + encode(fileId) + "?alt=media"))
                .header("Authorization", "Bearer " + token())
                .timeout(Duration.ofMinutes(5))
                .GET();
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            request.header("Range", rangeHeader);
        }
        return send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
    }

    /** Thư mục chứa file: dùng id cấu hình sẵn nếu có, không thì tìm hoặc tạo thư mục do app sở hữu. */
    private synchronized String folderId() throws IOException {
        if (configuredFolderId != null) {
            return configuredFolderId;
        }
        String query = "name='" + FOLDER_NAME + "' and mimeType='" + FOLDER_MIME + "' and trashed=false";
        HttpRequest find = HttpRequest.newBuilder(URI.create(apiBase + "/drive/v3/files?fields=files(id)&q=" + encode(query)))
                .header("Authorization", "Bearer " + token()).timeout(Duration.ofSeconds(30)).GET().build();
        HttpResponse<String> found = send(find, HttpResponse.BodyHandlers.ofString());
        if (found.statusCode() / 100 == 2) {
            JsonNode files = mapper.readTree(found.body()).path("files");
            if (files.isArray() && !files.isEmpty()) {
                configuredFolderId = files.get(0).path("id").asText();
                return configuredFolderId;
            }
        }
        String body = mapper.writeValueAsString(Map.of("name", FOLDER_NAME, "mimeType", FOLDER_MIME));
        HttpRequest create = HttpRequest.newBuilder(URI.create(apiBase + "/drive/v3/files?fields=id"))
                .header("Authorization", "Bearer " + token())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> created = send(create, HttpResponse.BodyHandlers.ofString());
        if (created.statusCode() / 100 != 2) {
            throw new IOException("Không tạo được thư mục trên Google Drive (mã " + created.statusCode() + ")");
        }
        configuredFolderId = mapper.readTree(created.body()).path("id").asText();
        log.info("Đã tạo thư mục \"{}\" trên Google Drive", FOLDER_NAME);
        return configuredFolderId;
    }

    /** Access token có hạn ngắn (khoảng 1 giờ): tự đổi từ refresh token khi sắp hết. */
    private synchronized String token() throws IOException {
        if (accessToken != null && System.currentTimeMillis() < accessTokenExpiresAtMs - 60_000) {
            return accessToken;
        }
        String form = "client_id=" + encode(clientId) + "&client_secret=" + encode(clientSecret)
                + "&refresh_token=" + encode(refreshToken) + "&grant_type=refresh_token";
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> response = send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Không đổi được access token Google (mã " + response.statusCode()
                    + "). Refresh token có thể đã bị thu hồi hoặc hết hạn: " + abbreviate(response.body()));
        }
        JsonNode json = mapper.readTree(response.body());
        accessToken = json.path("access_token").asText();
        accessTokenExpiresAtMs = System.currentTimeMillis() + json.path("expires_in").asLong(3600) * 1000;
        return accessToken;
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
        try {
            return http.send(request, handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Bị ngắt khi gọi Google Drive", e);
        }
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String abbreviate(String s) {
        return s == null ? "" : (s.length() > 300 ? s.substring(0, 300) + "…" : s);
    }
}
