package dev.knalis.assignment.repository;

import dev.knalis.assignment.entity.Submission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {
    
    Page<Submission> findAllByAssignmentId(UUID assignmentId, Pageable pageable);
    
    long countByAssignmentIdAndUserId(UUID assignmentId, UUID userId);

    boolean existsByAssignmentIdAndUserId(UUID assignmentId, UUID userId);

    List<Submission> findAllByUserIdOrderBySubmittedAtDesc(UUID userId);

    List<Submission> findTop20ByAssignmentIdInOrderBySubmittedAtDesc(Collection<UUID> assignmentIds);
}
