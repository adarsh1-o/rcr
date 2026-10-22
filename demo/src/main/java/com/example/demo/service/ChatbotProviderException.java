package com.example.demo.service;

import org.springframework.http.HttpStatus;

public class ChatbotProviderException extends RuntimeException {
    private final HttpStatus status;

    public ChatbotProviderException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
