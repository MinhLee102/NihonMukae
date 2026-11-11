package com.nihonmukae.backend.shadowing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nihonmukae.backend.shadowing.ShadowingDtos.Segment;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShadowingValidationTest {

    @Test
    void acceptsOrderedSegmentsAndTrimsText() {
        List<Segment> out = ShadowingService.validSegments(List.of(
                new Segment(0, 2.456, "  こんにちは "), new Segment(2.456, 5, "元気ですか")));
        assertEquals("こんにちは", out.get(0).text());
        assertEquals(2.46, out.get(0).end());
    }

    @Test
    void rejectsBadTimingEmptyTextAndEmptyList() {
        assertThrows(IllegalArgumentException.class, () -> ShadowingService.validSegments(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> ShadowingService.validSegments(List.of(new Segment(3, 3, "a"))));
        assertThrows(IllegalArgumentException.class,
                () -> ShadowingService.validSegments(List.of(new Segment(5, 6, "a"), new Segment(1, 2, "b"))));
        assertThrows(IllegalArgumentException.class,
                () -> ShadowingService.validSegments(List.of(new Segment(0, 1, "   "))));
    }

    @Test
    void validatesVideoId() {
        assertEquals("dQw4w9WgXcQ", ShadowingService.validVideoId(" dQw4w9WgXcQ "));
        assertThrows(IllegalArgumentException.class, () -> ShadowingService.validVideoId("short"));
        assertThrows(IllegalArgumentException.class, () -> ShadowingService.validVideoId("dQw4w9WgXc<"));
    }
}
