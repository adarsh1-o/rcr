package com.example.demo.service;

import com.example.demo.entity.Assignment;
import com.example.demo.entity.AssignmentResource;
import com.example.demo.entity.Course;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.AssignmentRepository;
import com.example.demo.repository.AssignmentResourceRepository;
import com.example.demo.repository.CourseRepository;
import com.example.demo.repository.EnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignmentResourceServiceTest {
    @Mock private AssignmentRepository assignmentRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private AssignmentResourceRepository resourceRepository;

    @TempDir
    Path storageRoot;

    private AssignmentResourceService resourceService;
    private AssignmentFileStorageService fileStorage;
    private User faculty;
    private User anotherFaculty;
    private User student;
    private Course course;
    private Assignment assignment;

    @BeforeEach
    void setUp() {
        AssignmentService assignmentService = new AssignmentService(
                assignmentRepository, courseRepository, enrollmentRepository);
        fileStorage = new AssignmentFileStorageService(storageRoot, 10 * 1024 * 1024);
        resourceService = new AssignmentResourceService(resourceRepository, assignmentService, fileStorage);
        faculty = user(1L, Role.FACULTY);
        anotherFaculty = user(2L, Role.FACULTY);
        student = user(3L, Role.STUDENT);
        course = Course.builder().id(10L).title("Math").faculty(faculty).build();
        assignment = Assignment.builder().id(20L).title("Algebra").course(course).build();
    }

    @Test
    void owningFacultyCanUploadResource() throws Exception {
        when(assignmentRepository.findById(20L)).thenReturn(Optional.of(assignment));
        when(resourceRepository.save(any())).thenAnswer(invocation -> {
            AssignmentResource resource = invocation.getArgument(0);
            resource.setId(40L);
            resource.prePersist();
            return resource;
        });

        var response = resourceService.upload(20L, pdf("instructions.pdf"), faculty);

        assertEquals(40L, response.getId());
        assertEquals("instructions.pdf", response.getOriginalFilename());
        assertEquals("application/pdf", response.getContentType());
        assertNotNull(response.getUploadedAt());
        verify(resourceRepository).save(argThat(resource ->
                resource.getAssignment() == assignment && resource.getUploadedBy() == faculty));
        assertEquals(1, countStoredFiles());
    }

    @Test
    void anotherFacultyCannotUploadToTheCourse() throws Exception {
        when(assignmentRepository.findById(20L)).thenReturn(Optional.of(assignment));

        assertThrows(AccessDeniedException.class,
                () -> resourceService.upload(20L, pdf("instructions.pdf"), anotherFaculty));

        verifyNoInteractions(resourceRepository);
        assertEquals(0, countStoredFiles());
    }

    @Test
    void enrolledStudentCanListAndDownloadAssignmentResources() throws Exception {
        when(assignmentRepository.findById(20L)).thenReturn(Optional.of(assignment));
        when(enrollmentRepository.existsByStudentAndCourse(student, course)).thenReturn(true);
        var stored = fileStorage.store(pdf("guide.pdf"));
        AssignmentResource resource = resource(stored);
        when(resourceRepository.findByAssignment_IdOrderByUploadedAtDesc(20L)).thenReturn(List.of(resource));
        when(resourceRepository.findByIdAndAssignment_Id(30L, 20L)).thenReturn(Optional.of(resource));

        var listed = resourceService.list(20L, student);
        var download = resourceService.download(20L, 30L, student);

        assertEquals(1, listed.size());
        assertEquals("guide.pdf", listed.get(0).getOriginalFilename());
        assertArrayEquals("%PDF-1.7\nbody".getBytes(), Files.readAllBytes(download.body().getFile().toPath()));
    }

    @Test
    void unenrolledStudentCannotListOrDownloadResources() {
        when(assignmentRepository.findById(20L)).thenReturn(Optional.of(assignment));
        when(enrollmentRepository.existsByStudentAndCourse(student, course)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> resourceService.list(20L, student));
        assertThrows(AccessDeniedException.class, () -> resourceService.download(20L, 30L, student));
        verifyNoInteractions(resourceRepository);
    }

    @Test
    void downloadRejectsAResourceThatBelongsToAnotherAssignment() throws Exception {
        when(assignmentRepository.findById(20L)).thenReturn(Optional.of(assignment));
        when(resourceRepository.findByIdAndAssignment_Id(99L, 20L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> resourceService.download(20L, 99L, faculty));
        assertEquals(0, countStoredFiles());
    }

    private MockMultipartFile pdf(String filename) {
        return new MockMultipartFile("file", filename, "application/pdf", "%PDF-1.7\nbody".getBytes());
    }

    private AssignmentResource resource(AssignmentFileStorageService.StoredFile stored) {
        return AssignmentResource.builder()
                .id(30L)
                .assignment(assignment)
                .uploadedBy(faculty)
                .originalFilename(stored.originalFilename())
                .storageId(stored.storageId())
                .fileSize(stored.fileSize())
                .contentType(stored.contentType())
                .uploadedAt(Instant.now())
                .build();
    }

    private long countStoredFiles() throws Exception {
        if (!Files.exists(storageRoot)) return 0;
        try (var paths = Files.list(storageRoot)) {
            return paths.count();
        }
    }

    private static User user(Long id, Role role) {
        return User.builder().id(id).name("User " + id).email("u" + id + "@example.test").role(role).build();
    }
}
