package com.example.minibank.security;

import com.example.minibank.exception.TooManyLoginAttemptsException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Tracks failed logins by client address and normalized email for a rolling window. */
@Component
public class LoginAttemptLimiter {

    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MILLIS = Duration.ofMinutes(1).toMillis();

    private final Map<String, ArrayDeque<Long>> failures = new HashMap<>();

    public synchronized void checkAllowed(String clientAddress, String email) {
        long now = System.currentTimeMillis();
        pruneExpired(now);
        if (count("ip:" + clientAddress, now) >= MAX_FAILURES
                || count("email:" + normalize(email), now) >= MAX_FAILURES) {
            throw new TooManyLoginAttemptsException();
        }
    }

    public synchronized void recordFailure(String clientAddress, String email) {
        long now = System.currentTimeMillis();
        pruneExpired(now);
        failures.computeIfAbsent("ip:" + clientAddress, ignored -> new ArrayDeque<>()).addLast(now);
        failures.computeIfAbsent("email:" + normalize(email), ignored -> new ArrayDeque<>()).addLast(now);
    }

    private int count(String key, long now) {
        ArrayDeque<Long> timestamps = failures.get(key);
        return timestamps == null ? 0 : timestamps.size();
    }

    private void pruneExpired(long now) {
        failures.values().forEach(timestamps -> {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= WINDOW_MILLIS) {
                timestamps.removeFirst();
            }
        });
        failures.values().removeIf(ArrayDeque::isEmpty);
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
