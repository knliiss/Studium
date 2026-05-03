package dev.knalis.assignment.repository;

import dev.knalis.assignment.entity.AssignmentOutboxEvent;
import dev.knalis.assignment.entity.AssignmentOutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentOutboxRepository extends JpaRepository<AssignmentOutboxEvent, UUID> {
    
    @Query(value = """
            select id
            from assignment.assignment_outbox_events
            where status in ('PENDING', 'RETRY')
              and next_attempt_at <= :now
            order by created_at asc
            limit :batchSize
            for update skip locked
            """, nativeQuery = true)
    List<UUID> lockNextBatchIds(@Param("now") Instant now, @Param("batchSize") int batchSize);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from AssignmentOutboxEvent event where event.id = :id")
    Optional<AssignmentOutboxEvent> findWithLockingById(@Param("id") UUID id);
    
    @Modifying
    @Query("""
            update AssignmentOutboxEvent event
            set event.status = :retryStatus,
                event.nextAttemptAt = :now,
                event.processingStartedAt = null,
                event.lastError = coalesce(event.lastError, 'Recovered after timeout')
            where event.status = :processingStatus
              and event.processingStartedAt < :cutoff
            """)
    int recoverTimedOut(
            @Param("processingStatus") AssignmentOutboxStatus processingStatus,
            @Param("retryStatus") AssignmentOutboxStatus retryStatus,
            @Param("cutoff") Instant cutoff,
            @Param("now") Instant now
    );
    
    @Modifying
    @Query("""
            delete from AssignmentOutboxEvent event
            where event.status = :publishedStatus
              and event.publishedAt < :cutoff
            """)
    int deletePublishedOlderThan(
            @Param("publishedStatus") AssignmentOutboxStatus publishedStatus,
            @Param("cutoff") Instant cutoff
    );
}
