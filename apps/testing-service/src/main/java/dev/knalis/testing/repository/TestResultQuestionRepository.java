package dev.knalis.testing.repository;

import dev.knalis.testing.entity.TestResultQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestResultQuestionRepository extends JpaRepository<TestResultQuestion, UUID> {

    List<TestResultQuestion> findAllByResultIdOrderByQuestionOrderIndexAscCreatedAtAsc(UUID resultId);

    Optional<TestResultQuestion> findByResultIdAndQuestionId(UUID resultId, UUID questionId);

    long countByResultId(UUID resultId);

    int deleteAllByResultId(UUID resultId);

    List<TestResultQuestion> findAllByResultIdIn(Collection<UUID> resultIds);
}
