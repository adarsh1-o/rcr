package com.example.demo.controller;

import com.example.demo.dto.AssignmentRequest;
import com.example.demo.dto.AssignmentResourceResponse;
import com.example.demo.dto.AssignmentResponse;
import com.example.demo.entity.User;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AssignmentService;
import com.example.demo.service.AssignmentResourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class AssignmentController {
    private final AssignmentService assignmentService;
    private final AssignmentResourceService assignmentResourceService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<AssignmentResponse>> getAll(Authentication authentication) {
        return ResponseEntity.ok(assignmentService.getAllAssignments(currentUser(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssignmentResponse> getById(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(assignmentService.getAssignmentById(id, currentUser(authentication)));
    }

    @GetMapping("/course/{courseId}")
    public ResponseEntity<List<AssignmentResponse>> getByCourse(@PathVariable Long courseId, Authentication authentication) {
        return ResponseEntity.ok(assignmentService.getAssignmentsByCourse(courseId, currentUser(authentication)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','FACULTY')")
    public ResponseEntity<AssignmentResponse> create(@Valid @RequestBody AssignmentRequest req, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assignmentService.createAssignment(req, currentUser(authentication)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FACULTY')")
    public ResponseEntity<AssignmentResponse> update(@PathVariable Long id, @Valid @RequestBody AssignmentRequest req,
                                                     Authentication authentication) {
        return ResponseEntity.ok(assignmentService.updateAssignment(id, req, currentUser(authentication)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','FACULTY')")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        assignmentResourceService.deleteAssignment(id, currentUser(authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/resources", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','FACULTY')")
    public ResponseEntity<AssignmentResourceResponse> uploadResource(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assignmentResourceService.upload(id, file, currentUser(authentication)));
    }

    @GetMapping("/{id}/resources")
    public ResponseEntity<List<AssignmentResourceResponse>> getResources(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(assignmentResourceService.list(id, currentUser(authentication)));
    }

    @GetMapping("/{id}/resources/{resourceId}/download")
    public ResponseEntity<Resource> downloadResource(
            @PathVariable Long id,
            @PathVariable Long resourceId,
            Authentication authentication) {
        AssignmentResourceService.Download download = assignmentResourceService.download(
                id, resourceId, currentUser(authentication));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.metadata().getContentType()))
                .contentLength(download.metadata().getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.metadata().getOriginalFilename(), StandardCharsets.UTF_8)
                        .build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(download.body());
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
