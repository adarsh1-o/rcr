package com.example.demo.service;

import com.example.demo.entity.ChatMessage;

import java.util.List;

public interface ChatbotProvider {
    String generateReply(String message, List<ChatMessage> recentMessages, boolean useWebSearch);
}
