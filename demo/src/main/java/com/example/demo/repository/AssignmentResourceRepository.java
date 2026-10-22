package com.example.demo.repository;

import com.example.demo.entity.AssignmentResource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssignmentResourceRepository extends JpaRepository<AssignmentResource, Long> {
    List<AssignmentResource> findByAssignment_IdOrderByUploadedAtDesc(Long assignmentId);
    Optional<AssignmentResource> findByIdAndAssignment_Id(Long id, Long assignmentId);
}
