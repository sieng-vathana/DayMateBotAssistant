package com.daymate.bot.core.security;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserRateLimiter {
    static final int LIMIT = 30;
    static final long WINDOW_MS = 10_000;

    private final ConcurrentHashMap<Long, ArrayDeque<Long>> hits = new ConcurrentHashMap<>();

    public boolean allow(Long userId) {
        if (userId == null) {
            return true;
        }
        long now = System.currentTimeMillis();
        ArrayDeque<Long> deque = hits.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && now - deque.peekFirst() > WINDOW_MS) {
                deque.removeFirst();
            }
            if (deque.size() >= LIMIT) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }
}
