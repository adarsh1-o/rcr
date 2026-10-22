package com.example.demo.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatbotRateLimiterTest {
    @Test
    void limitsEachStudentToTwentyRequestsPerMinute() {
        ChatbotRateLimiter limiter = new ChatbotRateLimiter();
        for (int i = 0; i < 20; i++) {
            limiter.check(7L);
        }

        ChatbotProviderException error = assertThrows(ChatbotProviderException.class,
                () -> limiter.check(7L));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatus());

        // A separate student has an independent allowance.
        limiter.check(8L);
    }
}
