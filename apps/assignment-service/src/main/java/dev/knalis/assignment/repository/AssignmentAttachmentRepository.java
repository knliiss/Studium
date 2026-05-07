package dev.knalis.assignment.repository;

import dev.knalis.assignment.entity.AssignmentAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentAttachmentRepository extends JpaRepository<AssignmentAttachment, UUID> {

    List<AssignmentAttachment> findAllByAssignmentIdOrderByCreatedAtAsc(UUID assignmentId);

    Optional<AssignmentAttachment> findByIdAndAssignmentId(UUID id, UUID assignmentId);

    boolean existsByAssignmentId(UUID assignmentId);
}
