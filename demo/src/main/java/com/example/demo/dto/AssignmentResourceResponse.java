package com.example.demo.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class AssignmentResourceResponse {
    private Long id;
    private String originalFilename;
    private long fileSize;
    private String contentType;
    private Instant uploadedAt;
}
