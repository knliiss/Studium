package dev.knalis.analytics.repository;

import dev.knalis.analytics.entity.RiskLevel;
import dev.knalis.analytics.entity.StudentProgressSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentProgressSnapshotRepository extends JpaRepository<StudentProgressSnapshot, UUID> {
    
    Optional<StudentProgressSnapshot> findByUserIdAndGroupId(UUID userId, UUID groupId);
    
    Optional<StudentProgressSnapshot> findByUserIdAndGroupIdIsNull(UUID userId);
    
    List<StudentProgressSnapshot> findAllByUserIdAndGroupIdIsNotNullOrderByUpdatedAtDesc(UUID userId);
    
    List<StudentProgressSnapshot> findAllByGroupIdOrderByUpdatedAtDesc(UUID groupId);

    Page<StudentProgressSnapshot> findAllByGroupId(UUID groupId, Pageable pageable);
    
    List<StudentProgressSnapshot> findAllByGroupIdIsNullOrderByUpdatedAtDesc();
    
    long countByGroupIdAndRiskLevel(UUID groupId, RiskLevel riskLevel);
    
    long countByGroupIdAndRiskLevelNot(UUID groupId, RiskLevel riskLevel);
    
    long countByGroupIdIsNullAndRiskLevel(RiskLevel riskLevel);
    
    long countByGroupIdIsNullAndRiskLevelNot(RiskLevel riskLevel);
}
