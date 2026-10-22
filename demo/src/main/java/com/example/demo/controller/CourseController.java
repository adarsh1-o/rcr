package com.example.demo.controller;

import com.example.demo.dto.CourseRequest;
import com.example.demo.dto.CourseResponse;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<CourseResponse>> getAllCourses() {
        return ResponseEntity.ok(courseService.getAllCourses());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseResponse> getCourseById(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.getCourseById(id));
    }

    @GetMapping("/faculty/{facultyId}")
    public ResponseEntity<java.util.List<CourseResponse>> getCoursesByFaculty(@PathVariable Long facultyId) {
        return ResponseEntity.ok(courseService.getCoursesByFacultyId(facultyId));
    }

    @GetMapping("/faculty/me")
    @PreAuthorize("hasRole('FACULTY')")
    public ResponseEntity<List<CourseResponse>> getMyCourses(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        return ResponseEntity.ok(courseService.getCoursesByFacultyId(currentUser.getId()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY')")
    public ResponseEntity<CourseResponse> createCourse(@Valid @RequestBody CourseRequest request,
                                                        Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(courseService.createCourse(request, currentUser.getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY')")
    public ResponseEntity<CourseResponse> updateCourse(@PathVariable Long id,
                                                        @Valid @RequestBody CourseRequest request,
                                                        Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        return ResponseEntity.ok(courseService.updateCourse(id, request, currentUser.getId(), isAdmin));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY')")
    public ResponseEntity<Void> deleteCourse(@PathVariable Long id, Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        courseService.deleteCourse(id, currentUser.getId(), isAdmin);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/links")
    @PreAuthorize("hasAnyRole('ADMIN', 'FACULTY')")
    public ResponseEntity<com.example.demo.dto.LearningLinkResponse> addLearningLink(@PathVariable Long id,
                                                                                      @Valid @RequestBody com.example.demo.dto.LearningLinkRequest req,
                                                                                      Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;
        com.example.demo.dto.LearningLinkResponse resp = courseService.addLearningLink(id, req, currentUser.getId(), isAdmin);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    private User getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
