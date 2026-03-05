package com.example.chatbot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenRateLimitService {
    private static final long WINDOW_MILLIS = 60_000L;

    @Value("${app.rate-limit.enabled}")
    private boolean enabled;

    @Value("${app.rate-limit.requests-per-minute}")
    private int requestsPerMinute;

    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key) {
        if (!enabled) {
            return true;
        }

        String effectiveKey = (key == null || key.isBlank()) ? "anonymous" : key;
        long now = System.currentTimeMillis();
        long minAllowedTs = now - WINDOW_MILLIS;

        Deque<Long> deque = buckets.computeIfAbsent(effectiveKey, ignored -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() < minAllowedTs) {
                deque.pollFirst();
            }

            if (deque.size() >= requestsPerMinute) {
                return false;
            }

            deque.addLast(now);
            return true;
        }
    }

    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }
}
