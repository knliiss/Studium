package dev.knalis.education.repository;

import dev.knalis.education.entity.GroupCurriculumOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupCurriculumOverrideRepository extends JpaRepository<GroupCurriculumOverride, UUID> {

    boolean existsByGroupIdAndSubjectId(UUID groupId, UUID subjectId);

    List<GroupCurriculumOverride> findAllByGroupIdOrderByCreatedAtAsc(UUID groupId);

    List<GroupCurriculumOverride> findAllByGroupIdAndSubjectIdIn(UUID groupId, Collection<UUID> subjectIds);

    Optional<GroupCurriculumOverride> findByIdAndGroupId(UUID id, UUID groupId);
}
