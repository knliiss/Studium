package dev.knalis.testing.repository;

import dev.knalis.testing.entity.TestAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestAttemptRepository extends JpaRepository<TestAttempt, UUID> {

    long countByTestIdAndUserId(UUID testId, UUID userId);

    Optional<TestAttempt> findFirstByTestIdAndUserIdAndCompletedAtIsNullOrderByStartedAtDesc(UUID testId, UUID userId);

    List<TestAttempt> findAllByUserId(UUID userId);
}
