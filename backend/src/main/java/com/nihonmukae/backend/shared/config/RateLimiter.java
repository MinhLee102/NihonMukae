package com.nihonmukae.backend.shared.config;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed window counter kept in memory: at most {@code limit} hits per key in each {@code windowMillis}.
 * One backend instance runs in production, so no shared store (Redis) is needed.
 */
public class RateLimiter {

    private record Window(long start, int count) {
    }

    private final int limit;
    private final long windowMillis;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private volatile long lastSweep;

    public RateLimiter(int limit, long windowMillis) {
        this.limit = limit;
        this.windowMillis = windowMillis;
    }

    /** @return true if the hit is allowed, false if the key has used up its quota in the current window */
    public boolean tryAcquire(String key, long nowMillis) {
        sweep(nowMillis);
        Window w = windows.compute(key, (k, old) ->
                old == null || nowMillis - old.start >= windowMillis ? new Window(nowMillis, 1) : new Window(old.start, old.count + 1));
        return w.count <= limit;
    }

    /** Drops expired windows now and then so the map cannot grow without bound. */
    private void sweep(long nowMillis) {
        if (nowMillis - lastSweep < windowMillis) {
            return;
        }
        lastSweep = nowMillis;
        for (Iterator<Window> it = windows.values().iterator(); it.hasNext(); ) {
            if (nowMillis - it.next().start >= windowMillis) {
                it.remove();
            }
        }
    }
}
