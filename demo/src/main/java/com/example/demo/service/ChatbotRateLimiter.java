package com.example.demo.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ChatbotRateLimiter {
    private static final int REQUESTS_PER_WINDOW = 20;
    private static final int MAX_TRACKED_STUDENTS = 10_000;
    private static final long WINDOW_SECONDS = 60;
    private final Map<Long, Deque<Instant>> requestsByStudent = new LinkedHashMap<>(16, 0.75f, true);

    public synchronized void check(Long studentId) {
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(WINDOW_SECONDS);
        Deque<Instant> requests = requestsByStudent.computeIfAbsent(studentId, ignored -> new ArrayDeque<>());
        while (!requests.isEmpty() && !requests.peekFirst().isAfter(cutoff)) {
            requests.removeFirst();
        }
        if (requests.size() >= REQUESTS_PER_WINDOW) {
            throw new ChatbotProviderException(HttpStatus.TOO_MANY_REQUESTS,
                    "You have sent too many assistant messages. Please wait a minute and try again.");
        }
        requests.addLast(now);
        trimExpiredEntries(cutoff);
    }

    private void trimExpiredEntries(Instant cutoff) {
        if (requestsByStudent.size() <= MAX_TRACKED_STUDENTS) return;

        Iterator<Map.Entry<Long, Deque<Instant>>> iterator = requestsByStudent.entrySet().iterator();
        while (iterator.hasNext()) {
            Deque<Instant> requests = iterator.next().getValue();
            while (!requests.isEmpty() && !requests.peekFirst().isAfter(cutoff)) {
                requests.removeFirst();
            }
            if (requests.isEmpty()) iterator.remove();
        }
        while (requestsByStudent.size() > MAX_TRACKED_STUDENTS) {
            requestsByStudent.remove(requestsByStudent.keySet().iterator().next());
        }
    }
}
