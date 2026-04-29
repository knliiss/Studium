package dev.knalis.education.repository;

import dev.knalis.education.entity.GroupStudent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupStudentRepository extends JpaRepository<GroupStudent, UUID> {
    
    boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);
    
    List<GroupStudent> findAllByGroupIdOrderByCreatedAtAsc(UUID groupId);
    
    List<GroupStudent> findAllByUserIdOrderByCreatedAtAsc(UUID userId);

    List<GroupStudent> findAllByUserIdIn(List<UUID> userIds);

    Optional<GroupStudent> findByGroupIdAndUserId(UUID groupId, UUID userId);
}
