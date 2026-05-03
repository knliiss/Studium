package dev.knalis.testing.repository;

import dev.knalis.testing.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    List<Question> findAllByTestIdOrderByOrderIndexAscCreatedAtAsc(UUID testId);

    List<Question> findAllByTestId(UUID testId);

    @Query("""
            select coalesce(sum(question.points), 0)
            from Question question
            where question.testId = :testId
            """)
    int sumPointsByTestId(@Param("testId") UUID testId);

    void deleteAllByTestId(UUID testId);
}
