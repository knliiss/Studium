package dev.knalis.analytics.repository;

import dev.knalis.analytics.entity.TeacherAnalyticsSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TeacherAnalyticsSnapshotRepository extends JpaRepository<TeacherAnalyticsSnapshot, UUID> {
    
    Optional<TeacherAnalyticsSnapshot> findByTeacherId(UUID teacherId);
}
