package com.nihonmukae.backend.shared;

import com.nihonmukae.backend.shared.config.RateLimiter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    @Test
    void allowsUpToLimitThenBlocks() {
        RateLimiter limiter = new RateLimiter(3, 1000);
        assertTrue(limiter.tryAcquire("ip", 0));
        assertTrue(limiter.tryAcquire("ip", 10));
        assertTrue(limiter.tryAcquire("ip", 20));
        assertFalse(limiter.tryAcquire("ip", 30));
    }

    @Test
    void keysAreIndependent() {
        RateLimiter limiter = new RateLimiter(1, 1000);
        assertTrue(limiter.tryAcquire("a", 0));
        assertTrue(limiter.tryAcquire("b", 0));
        assertFalse(limiter.tryAcquire("a", 1));
    }

    @Test
    void quotaResetsAfterWindow() {
        RateLimiter limiter = new RateLimiter(1, 1000);
        assertTrue(limiter.tryAcquire("ip", 0));
        assertFalse(limiter.tryAcquire("ip", 999));
        assertTrue(limiter.tryAcquire("ip", 1000));
    }
}
