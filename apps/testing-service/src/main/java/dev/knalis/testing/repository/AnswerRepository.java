package dev.knalis.testing.repository;

import dev.knalis.testing.entity.Answer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnswerRepository extends JpaRepository<Answer, UUID> {

    List<Answer> findAllByQuestionIdOrderByCreatedAtAsc(UUID questionId);

    List<Answer> findAllByQuestionIdInOrderByCreatedAtAsc(List<UUID> questionIds);

    boolean existsByQuestionIdAndCorrectTrue(UUID questionId);

    void deleteAllByQuestionId(UUID questionId);

    void deleteAllByQuestionIdIn(List<UUID> questionIds);
}
