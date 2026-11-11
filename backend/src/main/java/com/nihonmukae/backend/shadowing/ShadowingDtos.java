package com.nihonmukae.backend.shadowing;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ShadowingDtos {

    private ShadowingDtos() {
    }

    /** Một câu của phụ đề: thời điểm bắt đầu, kết thúc (giây) và nội dung. */
    public record Segment(double start, double end, String text) {
    }

    public record MaterialRequest(String videoId, String title, List<Segment> segments) {
    }

    public record MarksRequest(List<Integer> marked) {
    }

    public record MaterialSummary(UUID id, String videoId, String title, int segmentCount, int markedCount,
                                  OffsetDateTime updatedAt, OffsetDateTime lastPracticedAt) {
    }

    public record MaterialDetail(UUID id, String videoId, String title, List<Segment> segments, List<Integer> marked,
                                 OffsetDateTime lastPracticedAt) {
    }
}
