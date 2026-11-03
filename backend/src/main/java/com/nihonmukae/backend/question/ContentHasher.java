package com.nihonmukae.backend.question;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.List;

/** Băm nội dung đã chuẩn hóa để phát hiện câu hoặc nhóm trùng khi import. */
public final class ContentHasher {

    private ContentHasher() {
    }

    /** NFKC (gộp chữ full-width và half-width), bỏ khoảng trắng thừa. */
    static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFKC).replaceAll("\\s+", " ").trim();
    }

    public static String hashQuestion(String typeCode, int level, String question, List<String> options, String passage) {
        StringBuilder sb = new StringBuilder();
        sb.append("q|").append(typeCode).append('|').append(level).append('|').append(normalize(question));
        for (String option : options) {
            sb.append('|').append(normalize(option));
        }
        sb.append("|p|").append(normalize(passage));
        return sha256(sb.toString());
    }

    public static String hashGroup(String typeCode, int level, String passage, String audioUrl, String imageUrl) {
        return sha256("g|" + typeCode + '|' + level + '|' + normalize(passage) + '|' + normalize(audioUrl) + '|' + normalize(imageUrl));
    }

    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
