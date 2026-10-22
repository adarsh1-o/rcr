package com.example.demo.service;

import com.example.demo.dto.ChatMessageResponse;
import com.example.demo.dto.ChatRequest;
import com.example.demo.dto.ChatResponse;
import com.example.demo.entity.ChatMessage;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.ChatMessageRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatbotService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ChatbotProvider chatbotProvider;
    private final ChatbotRateLimiter chatbotRateLimiter;

    @Transactional
    public ChatResponse processMessage(ChatRequest req, User student) {
        requireStudent(student);
        chatbotRateLimiter.check(student.getId());

        chatMessageRepository.save(ChatMessage.builder()
                .content(req.getMessage().trim())
                .sender("USER")
                .student(student)
                .build());

        List<ChatMessage> recentMessages = recentContext(student.getId());
        boolean currentInformationRequested = asksForCurrentInformation(req.getMessage());
        String reply = chatbotProvider.generateReply(req.getMessage().trim(), recentMessages,
                currentInformationRequested);
        if (reply.length() > 2000) {
            reply = reply.substring(0, 1999) + "…";
        }

        chatMessageRepository.save(ChatMessage.builder()
                .content(reply)
                .sender("BOT")
                .student(student)
                .build());

        return ChatResponse.builder()
                .reply(reply)
                .studentId(student.getId())
                .sender("BOT")
                .createdAt(Instant.now().toString())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getHistory(User student) {
        requireStudent(student);
        if (!userRepository.existsById(student.getId())) {
            throw new ResourceNotFoundException("Student not found");
        }
        return chatMessageRepository.findByStudentIdOrderByCreatedAtAsc(student.getId()).stream()
                .map(message -> ChatMessageResponse.builder()
                        .id(message.getId())
                        .content(message.getContent())
                        .sender(message.getSender())
                        .createdAt(message.getCreatedAt() == null ? null : message.getCreatedAt().toString())
                        .build())
                .toList();
    }

    @Transactional
    public void clearHistory(User student) {
        requireStudent(student);
        if (!userRepository.existsById(student.getId())) {
            throw new ResourceNotFoundException("Student not found");
        }
        chatMessageRepository.deleteByStudentId(student.getId());
    }

    private List<ChatMessage> recentContext(Long studentId) {
        List<ChatMessage> messages = new ArrayList<>(
                chatMessageRepository.findTop12ByStudentIdOrderByCreatedAtDesc(studentId));
        Collections.reverse(messages);
        return messages;
    }

    private void requireStudent(User user) {
        if (user == null || user.getRole() != Role.STUDENT) {
            throw new org.springframework.security.access.AccessDeniedException("Student access required");
        }
    }

    private boolean asksForCurrentInformation(String message) {
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        return normalized.matches("(?s).*(\\btoday\\b|\\bcurrent(?:ly)?\\b|\\blatest\\b|\\brecent(?:ly)?\\b|\\bright now\\b|\\bthis week\\b|\\bthis month\\b|\\bthis year\\b|\\bnews\\b|\\bprice\\b|\\bweather\\b|\\bschedule\\b).*");
    }
}
