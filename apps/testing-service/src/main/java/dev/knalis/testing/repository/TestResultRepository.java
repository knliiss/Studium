package dev.knalis.testing.repository;

import dev.knalis.testing.entity.TestResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TestResultRepository extends JpaRepository<TestResult, UUID> {

    boolean existsByTestIdAndUserId(UUID testId, UUID userId);

    Page<TestResult> findAllByTestId(UUID testId, Pageable pageable);

    long countByTestId(UUID testId);

    void deleteAllByTestId(UUID testId);
}
