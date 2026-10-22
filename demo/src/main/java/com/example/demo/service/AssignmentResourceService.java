package com.example.demo.service;

import com.example.demo.dto.AssignmentResourceResponse;
import com.example.demo.entity.Assignment;
import com.example.demo.entity.AssignmentResource;
import com.example.demo.entity.User;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.AssignmentResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssignmentResourceService {
    private final AssignmentResourceRepository resourceRepository;
    private final AssignmentService assignmentService;
    private final AssignmentFileStorageService fileStorageService;

    @Transactional
    public AssignmentResourceResponse upload(Long assignmentId, MultipartFile file, User actor) {
        Assignment assignment = assignmentService.getAssignmentForManagement(assignmentId, actor);
        AssignmentFileStorageService.StoredFile stored = fileStorageService.store(file);
        try {
            AssignmentResource resource = AssignmentResource.builder()
                    .assignment(assignment)
                    .uploadedBy(actor)
                    .originalFilename(stored.originalFilename())
                    .storageId(stored.storageId())
                    .fileSize(stored.fileSize())
                    .contentType(stored.contentType())
                    .build();
            return toResponse(resourceRepository.save(resource));
        } catch (RuntimeException ex) {
            try {
                fileStorageService.delete(stored.storageId());
            } catch (RuntimeException cleanupFailure) {
                ex.addSuppressed(cleanupFailure);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<AssignmentResourceResponse> list(Long assignmentId, User actor) {
        assignmentService.getAuthorizedAssignment(assignmentId, actor);
        return resourceRepository.findByAssignment_IdOrderByUploadedAtDesc(assignmentId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Download download(Long assignmentId, Long resourceId, User actor) {
        assignmentService.getAuthorizedAssignment(assignmentId, actor);
        AssignmentResource resource = resourceRepository.findByIdAndAssignment_Id(resourceId, assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment resource not found"));
        Path path = fileStorageService.pathForDownload(resource.getStorageId());
        return new Download(toResponse(resource), new FileSystemResource(path));
    }

    @Transactional
    public void deleteAssignment(Long assignmentId, User actor) {
        assignmentService.getAssignmentForManagement(assignmentId, actor);
        List<AssignmentResource> resources = resourceRepository.findByAssignment_IdOrderByUploadedAtDesc(assignmentId);
        for (AssignmentResource resource : resources) {
            fileStorageService.delete(resource.getStorageId());
        }
        resourceRepository.deleteAll(resources);
        assignmentService.deleteAssignment(assignmentId, actor);
    }

    private AssignmentResourceResponse toResponse(AssignmentResource resource) {
        return AssignmentResourceResponse.builder()
                .id(resource.getId())
                .originalFilename(resource.getOriginalFilename())
                .fileSize(resource.getFileSize())
                .contentType(resource.getContentType())
                .uploadedAt(resource.getUploadedAt())
                .build();
    }

    public record Download(AssignmentResourceResponse metadata, Resource body) {}
}
