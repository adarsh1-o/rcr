package com.example.demo.repository;

import com.example.demo.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByStudentIdOrderByCreatedAtAsc(Long studentId);
    List<ChatMessage> findTop12ByStudentIdOrderByCreatedAtDesc(Long studentId);
    void deleteByStudentId(Long studentId);
}
