package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatMessageResponse {
    private Long id;
    private String content;
    private String sender;
    private String createdAt;
}
