package dev.knalis.testing.repository;

import dev.knalis.testing.entity.TestResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TestResultRepository extends JpaRepository<TestResult, UUID> {

    boolean existsByTestIdAndUserId(UUID testId, UUID userId);
}
