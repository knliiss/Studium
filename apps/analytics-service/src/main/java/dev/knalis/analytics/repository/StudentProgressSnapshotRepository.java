package dev.knalis.analytics.repository;

import dev.knalis.analytics.entity.RiskLevel;
import dev.knalis.analytics.entity.StudentProgressSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentProgressSnapshotRepository extends JpaRepository<StudentProgressSnapshot, UUID> {
    
    Optional<StudentProgressSnapshot> findFirstByUserIdAndGroupIdOrderByUpdatedAtDesc(UUID userId, UUID groupId);
    
    Optional<StudentProgressSnapshot> findFirstByUserIdAndGroupIdIsNullOrderByUpdatedAtDesc(UUID userId);
    
    List<StudentProgressSnapshot> findAllByUserIdAndGroupIdIsNotNullOrderByUpdatedAtDesc(UUID userId);
    
    List<StudentProgressSnapshot> findAllByGroupIdOrderByUpdatedAtDesc(UUID groupId);

    Page<StudentProgressSnapshot> findAllByGroupId(UUID groupId, Pageable pageable);
    
    List<StudentProgressSnapshot> findAllByGroupIdIsNullOrderByUpdatedAtDesc();
    
    long countByGroupIdAndRiskLevel(UUID groupId, RiskLevel riskLevel);
    
    long countByGroupIdAndRiskLevelNot(UUID groupId, RiskLevel riskLevel);
    
    long countByGroupIdIsNullAndRiskLevel(RiskLevel riskLevel);
    
    long countByGroupIdIsNullAndRiskLevelNot(RiskLevel riskLevel);

    @Modifying
    @Query(
            value = """
                    insert into student_progress_snapshots (id, user_id, group_id, updated_at)
                    values (:id, :userId, null, now())
                    on conflict (user_id) where group_id is null do nothing
                    """,
            nativeQuery = true
    )
    void insertUserSnapshotIfAbsent(@Param("id") UUID id, @Param("userId") UUID userId);

    @Modifying
    @Query(
            value = """
                    insert into student_progress_snapshots (id, user_id, group_id, updated_at)
                    values (:id, :userId, :groupId, now())
                    on conflict (user_id, group_id) do nothing
                    """,
            nativeQuery = true
    )
    void insertGroupSnapshotIfAbsent(@Param("id") UUID id, @Param("userId") UUID userId, @Param("groupId") UUID groupId);
}
