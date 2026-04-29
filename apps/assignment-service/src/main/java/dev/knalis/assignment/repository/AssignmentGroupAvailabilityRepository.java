package dev.knalis.assignment.repository;

import dev.knalis.assignment.entity.AssignmentGroupAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentGroupAvailabilityRepository extends JpaRepository<AssignmentGroupAvailability, UUID> {

    List<AssignmentGroupAvailability> findAllByAssignmentIdOrderByCreatedAtAsc(UUID assignmentId);

    Optional<AssignmentGroupAvailability> findByAssignmentIdAndGroupId(UUID assignmentId, UUID groupId);

    @Query("""
            select availability
            from AssignmentGroupAvailability availability
            where availability.assignmentId in :assignmentIds
              and availability.groupId in :groupIds
              and availability.visible = true
              and (availability.availableFrom is null or availability.availableFrom <= :now)
            order by availability.deadline asc
            """)
    List<AssignmentGroupAvailability> findAvailableForAssignmentsAndGroups(
            Collection<UUID> assignmentIds,
            Collection<UUID> groupIds,
            Instant now
    );

    @Query("""
            select count(availability) > 0
            from AssignmentGroupAvailability availability
            where availability.assignmentId = :assignmentId
              and availability.groupId in :groupIds
              and availability.visible = true
              and (availability.availableFrom is null or availability.availableFrom <= :now)
            """)
    boolean existsAvailableForGroups(UUID assignmentId, Collection<UUID> groupIds, Instant now);
}
