package com.example.demo.controller;

import com.example.demo.dto.ChatRequest;
import com.example.demo.dto.ChatResponse;
import com.example.demo.dto.ChatMessageResponse;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.ChatbotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;
    private final UserRepository userRepository;

    @PostMapping("/message")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ChatResponse> sendMessage(@Valid @RequestBody ChatRequest req,
                                                     Authentication authentication) {
        return ResponseEntity.ok(chatbotService.processMessage(req, currentStudent(authentication)));
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<ChatMessageResponse>> getHistory(Authentication authentication) {
        return ResponseEntity.ok(chatbotService.getHistory(currentStudent(authentication)));
    }

    @DeleteMapping("/history")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> clearHistory(Authentication authentication) {
        chatbotService.clearHistory(currentStudent(authentication));
        return ResponseEntity.noContent().build();
    }

    private User currentStudent(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Student not found"));
        if (user.getRole() != Role.STUDENT) {
            throw new org.springframework.security.access.AccessDeniedException("Student access required");
        }
        return user;
    }
}
