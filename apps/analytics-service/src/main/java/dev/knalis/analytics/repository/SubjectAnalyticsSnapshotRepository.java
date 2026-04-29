package dev.knalis.analytics.repository;

import dev.knalis.analytics.entity.SubjectAnalyticsSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubjectAnalyticsSnapshotRepository extends JpaRepository<SubjectAnalyticsSnapshot, UUID> {
    
    Optional<SubjectAnalyticsSnapshot> findBySubjectIdAndGroupId(UUID subjectId, UUID groupId);
    
    Optional<SubjectAnalyticsSnapshot> findBySubjectIdAndGroupIdIsNull(UUID subjectId);
    
    List<SubjectAnalyticsSnapshot> findAllBySubjectIdOrderByUpdatedAtDesc(UUID subjectId);

    Page<SubjectAnalyticsSnapshot> findAllBySubjectId(UUID subjectId, Pageable pageable);
    
    List<SubjectAnalyticsSnapshot> findAllByGroupIdInOrderByUpdatedAtDesc(Collection<UUID> groupIds);
}
