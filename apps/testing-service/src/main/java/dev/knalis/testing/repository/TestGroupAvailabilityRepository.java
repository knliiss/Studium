package dev.knalis.testing.repository;

import dev.knalis.testing.entity.TestGroupAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestGroupAvailabilityRepository extends JpaRepository<TestGroupAvailability, UUID> {

    List<TestGroupAvailability> findAllByTestIdOrderByCreatedAtAsc(UUID testId);

    Optional<TestGroupAvailability> findByTestIdAndGroupId(UUID testId, UUID groupId);

    @Query("""
            select availability
            from TestGroupAvailability availability
            where availability.testId in :testIds
              and availability.groupId in :groupIds
              and availability.visible = true
              and (availability.availableFrom is null or availability.availableFrom <= :now)
              and (availability.availableUntil is null or availability.availableUntil >= :now)
            order by availability.availableUntil asc nulls last
            """)
    List<TestGroupAvailability> findAvailableForTestsAndGroups(
            Collection<UUID> testIds,
            Collection<UUID> groupIds,
            Instant now
    );

    @Query("""
            select availability
            from TestGroupAvailability availability
            where availability.testId = :testId
              and availability.groupId in :groupIds
              and availability.visible = true
              and (availability.availableFrom is null or availability.availableFrom <= :now)
              and (availability.availableUntil is null or availability.availableUntil >= :now)
            order by availability.availableUntil asc nulls last
            """)
    List<TestGroupAvailability> findAvailableForTestAndGroups(UUID testId, Collection<UUID> groupIds, Instant now);

    void deleteAllByTestId(UUID testId);
}
