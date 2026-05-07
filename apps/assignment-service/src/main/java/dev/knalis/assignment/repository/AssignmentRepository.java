package dev.knalis.assignment.repository;

import dev.knalis.assignment.entity.Assignment;
import dev.knalis.assignment.entity.AssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {
    
    Page<Assignment> findAllByTopicId(UUID topicId, Pageable pageable);
    
    Page<Assignment> findAllByTopicIdAndStatus(
            UUID topicId,
            AssignmentStatus status,
            Pageable pageable
    );

    Page<Assignment> findAllByTitleContainingIgnoreCase(String title, Pageable pageable);

    Page<Assignment> findAllByTitleContainingIgnoreCaseAndStatus(
            String title,
            AssignmentStatus status,
            Pageable pageable
    );

    List<Assignment> findAllByStatusAndDeadlineBetween(AssignmentStatus status, Instant deadlineFrom, Instant deadlineTo);

    List<Assignment> findAllByTopicIdInAndStatus(Collection<UUID> topicIds, AssignmentStatus status);

    @Query("""
            select assignment
            from Assignment assignment
            where assignment.topicId = :topicId
              and assignment.status in :statuses
              and exists (
                  select availability.id
                  from AssignmentGroupAvailability availability
                  where availability.assignmentId = assignment.id
                    and availability.groupId in :groupIds
                    and availability.visible = true
                    and (availability.availableFrom is null or availability.availableFrom <= :now)
              )
            """)
    Page<Assignment> findAvailableByTopicIdForGroups(
            UUID topicId,
            Collection<AssignmentStatus> statuses,
            Collection<UUID> groupIds,
            Instant now,
            Pageable pageable
    );

    @Query("""
            select distinct assignment
            from Assignment assignment
            where assignment.topicId in :topicIds
              and assignment.status in :statuses
              and exists (
                  select availability.id
                  from AssignmentGroupAvailability availability
                  where availability.assignmentId = assignment.id
                    and availability.groupId in :groupIds
                    and availability.visible = true
                    and (availability.availableFrom is null or availability.availableFrom <= :now)
              )
            """)
    List<Assignment> findAvailableByTopicIdInForGroups(
            Collection<UUID> topicIds,
            Collection<AssignmentStatus> statuses,
            Collection<UUID> groupIds,
            Instant now
    );

    @Query("""
            select assignment
            from Assignment assignment
            where assignment.topicId = :topicId
              and (assignment.status in :visibleStatuses
                   or assignment.createdByUserId = :teacherId)
            """)
    Page<Assignment> findVisibleByTopicIdForTeacher(
            UUID topicId,
            UUID teacherId,
            @Param("visibleStatuses") Collection<AssignmentStatus> visibleStatuses,
            Pageable pageable
    );

    List<Assignment> findAllByCreatedByUserIdAndStatusInOrderByDeadlineAscUpdatedAtDesc(
            UUID createdByUserId,
            Collection<AssignmentStatus> statuses
    );

    long countByStatusAndDeadlineAfter(AssignmentStatus status, Instant deadline);
}
