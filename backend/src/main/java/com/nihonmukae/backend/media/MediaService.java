package com.nihonmukae.backend.media;

import com.nihonmukae.backend.media.MediaStorage.Opened;
import com.nihonmukae.backend.media.MediaValidator.Detected;
import com.nihonmukae.backend.media.MediaValidator.Kind;
import com.nihonmukae.backend.media.dto.MediaInfo;
import com.nihonmukae.backend.shared.entity.MediaFile;
import com.nihonmukae.backend.shared.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Upload và phát audio, ảnh của đề thi. Mọi upload đi qua cùng các lớp phòng thủ:
 * định dạng nhận diện bằng nội dung thật, giới hạn dung lượng từng file, giới hạn số lần upload mỗi giờ,
 * trần tổng dung lượng, chống trùng nội dung, và tên file lưu là UUID do server tạo.
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);

    private final MediaStorage storage;
    private final MediaFileRepository repository;
    private final long maxAudioBytes;
    private final long maxImageBytes;
    private final long maxTotalBytes;
    private final int maxUploadsPerHour;

    public MediaService(MediaStorage storage,
                        MediaFileRepository repository,
                        @Value("${app.media.max-audio-mb:60}") long maxAudioMb,
                        @Value("${app.media.max-image-mb:5}") long maxImageMb,
                        @Value("${app.media.max-total-mb:20480}") long maxTotalMb,
                        @Value("${app.media.max-uploads-per-hour:200}") int maxUploadsPerHour) {
        this.storage = storage;
        this.repository = repository;
        this.maxAudioBytes = maxAudioMb * 1024 * 1024;
        this.maxImageBytes = maxImageMb * 1024 * 1024;
        this.maxTotalBytes = maxTotalMb * 1024 * 1024;
        this.maxUploadsPerHour = maxUploadsPerHour;
    }

    public MediaInfo upload(UUID userId, MultipartFile file) throws IOException {
        long size = file.getSize();
        if (size <= 0) {
            throw new IllegalArgumentException("File rỗng");
        }

        Detected detected;
        try (InputStream in = file.getInputStream()) {
            byte[] header = new byte[MediaValidator.HEADER_LENGTH];
            int read = in.readNBytes(header, 0, header.length);
            detected = MediaValidator.detect(header, read);
        }

        long limit = detected.kind() == Kind.AUDIO ? maxAudioBytes : maxImageBytes;
        if (size > limit) {
            throw new IllegalArgumentException("File quá lớn: tối đa " + (limit / 1024 / 1024) + " MB cho "
                    + (detected.kind() == Kind.AUDIO ? "audio" : "ảnh"));
        }

        String sha256 = sha256(file);
        var existing = repository.findBySha256(sha256);
        if (existing.isPresent()) {
            return toInfo(existing.get(), true);
        }

        if (repository.countByUploadedByAndCreatedAtAfter(userId, ZonedDateTime.now().minusHours(1)) >= maxUploadsPerHour) {
            throw new IllegalArgumentException("Bạn upload quá nhiều trong 1 giờ qua, hãy thử lại sau");
        }
        if (repository.totalBytes() + size > maxTotalBytes) {
            throw new IllegalArgumentException("Kho media đã đầy (tối đa " + (maxTotalBytes / 1024 / 1024) + " MB)");
        }

        UUID id = UUID.randomUUID();
        String key;
        try (InputStream in = file.getInputStream()) {
            key = storage.put(id, in, size, detected.mimeType());
        }
        try {
            MediaFile saved = repository.save(MediaFile.builder()
                    .id(id).kind(detected.kind().code()).mimeType(detected.mimeType()).sizeBytes(size).sha256(sha256)
                    .originalName(cleanName(file.getOriginalFilename())).storage(storage.name()).storageKey(key)
                    .uploadedBy(userId).build());
            return toInfo(saved, false);
        } catch (RuntimeException e) {
            // Lưu thông tin thất bại thì dọn file vừa ghi, tránh để lại file mồ côi
            try {
                storage.delete(key);
            } catch (IOException cleanup) {
                log.warn("Không dọn được file mồ côi {}: {}", key, cleanup.getMessage());
            }
            throw e;
        }
    }

    /** Dữ liệu để phát lại 1 file, kèm thông tin để dựng header. */
    public record Playback(MediaFile file, Opened opened) {
    }

    public Playback open(UUID id, String rangeHeader) throws IOException {
        MediaFile file = repository.findById(id).orElseThrow(() -> new NotFoundException("Không tìm thấy file"));
        // Mọi bản ghi mới đều ghi nơi lưu hiện tại; file cũ ở nơi khác (đổi provider) thì không phát được
        if (!file.getStorage().equals(storage.name())) {
            throw new NotFoundException("File này đang nằm ở nơi lưu trữ không còn được bật");
        }
        return new Playback(file, storage.open(file.getStorageKey(), file.getSizeBytes(), rangeHeader));
    }

    private MediaInfo toInfo(MediaFile f, boolean duplicate) {
        return new MediaInfo(f.getId(), "/media/" + f.getId(), f.getKind(), f.getMimeType(), f.getSizeBytes(),
                f.getOriginalName(), duplicate);
    }

    private static String sha256(MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream()) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                md.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Chỉ để hiển thị cho admin: bỏ đường dẫn và ký tự điều khiển, cắt độ dài. */
    static String cleanName(String name) {
        if (name == null) {
            return null;
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        return base.length() > 255 ? base.substring(0, 255) : base;
    }
}
