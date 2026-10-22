package com.example.demo.controller;

import com.example.demo.dto.CreateMeetingRequest;
import com.example.demo.dto.JoinMeetingResponse;
import com.example.demo.dto.MeetingResponse;
import com.example.demo.entity.MeetingStatus;
import com.example.demo.entity.User;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.MeetingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;
    private final UserRepository userRepository;

    @GetMapping("/course/{courseId}")
    public ResponseEntity<List<MeetingResponse>> getByCourse(@PathVariable Long courseId, Authentication authentication) {
        return ResponseEntity.ok(meetingService.getMeetingsByCourse(courseId, currentUser(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MeetingResponse> getById(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(meetingService.getMeeting(id, currentUser(authentication)));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<JoinMeetingResponse> join(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(meetingService.joinMeeting(id, currentUser(authentication)));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MeetingResponse> create(@Valid @RequestBody CreateMeetingRequest req, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(meetingService.createMeeting(req, currentUser(authentication)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','FACULTY')")
    public ResponseEntity<MeetingResponse> updateStatus(@PathVariable Long id, @RequestParam MeetingStatus status, Authentication authentication) {
        return ResponseEntity.ok(meetingService.updateMeetingStatus(id, status, currentUser(authentication)));
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
