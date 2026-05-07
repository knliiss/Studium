package dev.knalis.education.repository;

import dev.knalis.education.entity.SubjectGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubjectGroupRepository extends JpaRepository<SubjectGroup, UUID> {

    boolean existsBySubjectIdAndGroupId(UUID subjectId, UUID groupId);

    List<SubjectGroup> findAllBySubjectId(UUID subjectId);

    List<SubjectGroup> findAllBySubjectIdOrderByCreatedAtAsc(UUID subjectId);

    List<SubjectGroup> findAllByGroupIdOrderByCreatedAtAsc(UUID groupId);

    Optional<SubjectGroup> findBySubjectIdAndGroupId(UUID subjectId, UUID groupId);

    void deleteAllBySubjectId(UUID subjectId);
}
