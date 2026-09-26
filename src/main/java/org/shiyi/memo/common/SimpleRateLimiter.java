package org.shiyi.memo.common;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 极简固定窗口限流，用于「同一 IP 一分钟只能请求几次验证码」这类场景。
 * 单机内存实现，多实例部署时需要换成 Redis。
 */
@Component
public class SimpleRateLimiter {

    private static final int MAX_KEYS = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int limit, Duration window) {
        long now = System.currentTimeMillis();
        if (windows.size() > MAX_KEYS) {
            windows.entrySet().removeIf(entry -> entry.getValue().expired(now));
        }
        Window w = windows.compute(key, (k, current) -> {
            if (current == null || current.expired(now)) {
                return new Window(now, window.toMillis());
            }
            current.count().incrementAndGet();
            return current;
        });
        return w.count().get() <= limit;
    }

    private record Window(long startMillis, long windowMillis, AtomicInteger count) {

        Window(long startMillis, long windowMillis) {
            this(startMillis, windowMillis, new AtomicInteger(1));
        }

        boolean expired(long now) {
            return now - startMillis > windowMillis;
        }
    }
}
