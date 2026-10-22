package com.example.demo.service;

import com.example.demo.dto.AssignmentRequest;
import com.example.demo.dto.AssignmentResponse;
import com.example.demo.entity.Assignment;
import com.example.demo.entity.Course;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.AssignmentRepository;
import com.example.demo.repository.CourseRepository;
import com.example.demo.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssignmentService {
    private static final double LEGACY_DEFAULT_MAX_MARKS = 100.0;

    private final AssignmentRepository assignmentRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;

    @Transactional(readOnly = true)
    public List<AssignmentResponse> getAllAssignments(User actor) {
        return assignmentRepository.findAll().stream()
                .filter(a -> canAccess(a.getCourse(), actor))
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AssignmentResponse getAssignmentById(Long id, User actor) {
        return toResponse(authorizedAssignment(id, actor));
    }

    @Transactional
    public AssignmentResponse createAssignment(AssignmentRequest request, User actor) {
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.FACULTY) {
            throw new AccessDeniedException("Access denied");
        }
        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        ensureCourseOwner(course, actor);
        validateAssignmentRequest(request);
        validateDeadline(request.getDueDate(), null);

        Assignment assignment = Assignment.builder()
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .dueDate(request.getDueDate())
                .maxMarks(request.getMaxMarks())
                .allowLateSubmission(Boolean.TRUE.equals(request.getAllowLateSubmission()))
                .course(course)
                .build();
        return toResponse(assignmentRepository.save(assignment));
    }

    @Transactional
    public AssignmentResponse updateAssignment(Long id, AssignmentRequest request, User actor) {
        Assignment assignment = authorizedAssignment(id, actor);
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.FACULTY) {
            throw new AccessDeniedException("Access denied");
        }
        ensureCourseOwner(assignment.getCourse(), actor);
        validateAssignmentRequest(request);
        validateDeadline(request.getDueDate(), assignment.getDueDate());
        assignment.setTitle(request.getTitle().trim());
        assignment.setDescription(request.getDescription());
        assignment.setDueDate(request.getDueDate());
        assignment.setMaxMarks(request.getMaxMarks());
        assignment.setAllowLateSubmission(Boolean.TRUE.equals(request.getAllowLateSubmission()));
        return toResponse(assignmentRepository.save(assignment));
    }

    @Transactional
    public void deleteAssignment(Long id, User actor) {
        Assignment assignment = authorizedAssignment(id, actor);
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.FACULTY) {
            throw new AccessDeniedException("Access denied");
        }
        ensureCourseOwner(assignment.getCourse(), actor);
        assignmentRepository.delete(assignment);
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> getAssignmentsByCourse(Long courseId, User actor) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found"));
        ensureCourseAccess(course, actor);
        return assignmentRepository.findByCourseId(courseId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Assignment getAuthorizedAssignment(Long id, User actor) {
        return authorizedAssignment(id, actor);
    }

    @Transactional(readOnly = true)
    public Assignment getAssignmentForManagement(Long id, User actor) {
        Assignment assignment = authorizedAssignment(id, actor);
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.FACULTY) {
            throw new AccessDeniedException("Access denied");
        }
        ensureCourseOwner(assignment.getCourse(), actor);
        return assignment;
    }

    private Assignment authorizedAssignment(Long id, User actor) {
        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));
        ensureCourseAccess(assignment.getCourse(), actor);
        return assignment;
    }

    private boolean canAccess(Course course, User actor) {
        return actor.getRole() == Role.ADMIN
                || (actor.getRole() == Role.FACULTY && course.getFaculty().getId().equals(actor.getId()))
                || (actor.getRole() == Role.STUDENT && enrollmentRepository.existsByStudentAndCourse(actor, course));
    }

    private void ensureCourseAccess(Course course, User actor) {
        if (!canAccess(course, actor)) throw new AccessDeniedException("Access denied");
    }

    private void ensureCourseOwner(Course course, User actor) {
        if (actor.getRole() != Role.ADMIN && !course.getFaculty().getId().equals(actor.getId())) {
            throw new AccessDeniedException("Access denied");
        }
    }

    private void validateDeadline(Instant dueDate, Instant existingDueDate) {
        if (dueDate != null && !dueDate.isAfter(Instant.now()) && !dueDate.equals(existingDueDate)) {
            throw new IllegalArgumentException("The assignment deadline must be in the future");
        }
    }

    private void validateAssignmentRequest(AssignmentRequest request) {
        Double maxMarks = request.getMaxMarks();
        if (maxMarks == null || !Double.isFinite(maxMarks) || maxMarks <= 0) {
            throw new IllegalArgumentException("Maximum marks must be a positive number");
        }
    }

    private AssignmentResponse toResponse(Assignment a) {
        return AssignmentResponse.builder()
                .id(a.getId()).title(a.getTitle()).description(a.getDescription())
                .dueDate(a.getDueDate())
                .maxMarks(a.getMaxMarks() == null ? LEGACY_DEFAULT_MAX_MARKS : a.getMaxMarks())
                .allowLateSubmission(Boolean.TRUE.equals(a.getAllowLateSubmission()))
                .createdAt(a.getCreatedAt()).updatedAt(a.getUpdatedAt())
                .courseId(a.getCourse().getId()).courseTitle(a.getCourse().getTitle())
                .build();
    }
}
