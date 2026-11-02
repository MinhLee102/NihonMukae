package com.nihonmukae.backend.media;

/**
 * Chỉ cho phép đúng các định dạng audio và ảnh cần cho đề thi, nhận diện bằng "magic bytes" ở đầu file
 * chứ không tin đuôi file hay Content-Type do client gửi (dễ giả). Mọi thứ khác bị từ chối, đặc biệt là
 * SVG, HTML và file thực thi.
 */
public final class MediaValidator {

    public enum Kind {
        AUDIO, IMAGE;

        public String code() {
            return name().toLowerCase();
        }
    }

    public record Detected(Kind kind, String mimeType) {
    }

    /** Số byte đầu file cần đọc để nhận diện định dạng. */
    public static final int HEADER_LENGTH = 16;

    private MediaValidator() {
    }

    /** @throws IllegalArgumentException nếu không phải định dạng được phép */
    public static Detected detect(byte[] h, int length) {
        if (length >= 3 && h[0] == 'I' && h[1] == 'D' && h[2] == '3') {
            return new Detected(Kind.AUDIO, "audio/mpeg");
        }
        // MP3 không có thẻ ID3: khung bắt đầu bằng 11 bit 1 (0xFF 0xEx)
        if (length >= 2 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xE0) == 0xE0) {
            return new Detected(Kind.AUDIO, "audio/mpeg");
        }
        if (length >= 4 && startsWith(h, "OggS")) {
            return new Detected(Kind.AUDIO, "audio/ogg");
        }
        if (length >= 12 && startsWith(h, "RIFF")) {
            if (matchesAt(h, 8, "WAVE")) {
                return new Detected(Kind.AUDIO, "audio/wav");
            }
            if (matchesAt(h, 8, "WEBP")) {
                return new Detected(Kind.IMAGE, "image/webp");
            }
        }
        if (length >= 12 && matchesAt(h, 4, "ftyp")) {
            String brand = new String(h, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (brand.equals("M4A ") || brand.equals("M4B ") || brand.equals("mp42") || brand.equals("isom")
                    || brand.equals("iso2") || brand.equals("mp41")) {
                return new Detected(Kind.AUDIO, "audio/mp4");
            }
        }
        if (length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') {
            return new Detected(Kind.IMAGE, "image/png");
        }
        if (length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return new Detected(Kind.IMAGE, "image/jpeg");
        }
        throw new IllegalArgumentException("Định dạng file không được hỗ trợ. Chỉ nhận mp3, m4a, ogg, wav (audio) và png, jpg, webp (ảnh)");
    }

    private static boolean startsWith(byte[] h, String ascii) {
        return matchesAt(h, 0, ascii);
    }

    private static boolean matchesAt(byte[] h, int offset, String ascii) {
        for (int i = 0; i < ascii.length(); i++) {
            if (h[offset + i] != (byte) ascii.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
