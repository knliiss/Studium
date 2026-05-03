package dev.knalis.assignment.repository;

import dev.knalis.assignment.entity.Grade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GradeRepository extends JpaRepository<Grade, UUID> {
    
    boolean existsBySubmissionId(UUID submissionId);

    Optional<Grade> findBySubmissionId(UUID submissionId);

    List<Grade> findAllBySubmissionIdIn(Collection<UUID> submissionIds);
}
