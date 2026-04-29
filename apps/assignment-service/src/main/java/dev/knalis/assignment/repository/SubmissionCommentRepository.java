package dev.knalis.assignment.repository;

import dev.knalis.assignment.entity.SubmissionComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubmissionCommentRepository extends JpaRepository<SubmissionComment, UUID> {

    List<SubmissionComment> findAllBySubmissionIdOrderByCreatedAtAsc(UUID submissionId);

    Optional<SubmissionComment> findByIdAndSubmissionId(UUID id, UUID submissionId);
}
