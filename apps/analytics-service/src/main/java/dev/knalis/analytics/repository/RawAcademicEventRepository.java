package dev.knalis.analytics.repository;

import dev.knalis.analytics.entity.RawAcademicEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RawAcademicEventRepository extends JpaRepository<RawAcademicEvent, UUID> {
    
    boolean existsByEventId(UUID eventId);
    
    List<RawAcademicEvent> findTop6ByUserIdAndEventTypeInOrderByOccurredAtDesc(
            UUID userId,
            Collection<String> eventTypes
    );
    
    List<RawAcademicEvent> findTop6ByUserIdAndGroupIdAndEventTypeInOrderByOccurredAtDesc(
            UUID userId,
            UUID groupId,
            Collection<String> eventTypes
    );
    
    Optional<RawAcademicEvent> findTopBySubmissionIdAndEventTypeOrderByOccurredAtDesc(UUID submissionId, String eventType);
    
    Optional<RawAcademicEvent> findTopByTestIdAndEventTypeOrderByOccurredAtDesc(UUID testId, String eventType);

    boolean existsByTeacherIdAndGroupIdIn(UUID teacherId, Collection<UUID> groupIds);

    @Query("""
            select distinct event.groupId
            from RawAcademicEvent event
            where event.teacherId = :teacherId
              and event.groupId is not null
            order by event.groupId
            """)
    List<UUID> findDistinctGroupIdsByTeacherId(UUID teacherId);
    
    @Query("""
            select count(distinct event.userId)
            from RawAcademicEvent event
            where event.subjectId = :subjectId
              and ((:groupId is null and event.groupId is null) or event.groupId = :groupId)
              and event.userId is not null
            """)
    long countDistinctUsersBySubjectIdAndGroupId(UUID subjectId, UUID groupId);
}
