package dev.knalis.assignment.repository;

import dev.knalis.assignment.entity.SubmissionAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubmissionAttachmentRepository extends JpaRepository<SubmissionAttachment, UUID> {

    List<SubmissionAttachment> findAllBySubmissionIdOrderByCreatedAtAsc(UUID submissionId);

    Optional<SubmissionAttachment> findByIdAndSubmissionId(UUID id, UUID submissionId);

    long countBySubmissionId(UUID submissionId);
}
