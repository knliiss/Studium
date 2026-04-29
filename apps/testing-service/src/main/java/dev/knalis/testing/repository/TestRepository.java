package dev.knalis.testing.repository;

import dev.knalis.testing.entity.Test;
import dev.knalis.testing.entity.TestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TestRepository extends JpaRepository<Test, UUID> {
    
    Page<Test> findAllByTopicId(UUID topicId, Pageable pageable);
    
    Page<Test> findAllByTopicIdAndStatus(UUID topicId, TestStatus status, Pageable pageable);

    List<Test> findAllByStatusAndAvailableUntilBetween(TestStatus status, Instant availableUntilFrom, Instant availableUntilTo);

    Page<Test> findAllByTitleContainingIgnoreCase(String title, Pageable pageable);

    Page<Test> findAllByTitleContainingIgnoreCaseAndStatus(
            String title,
            TestStatus status,
            Pageable pageable
    );

    @Query("""
            select test
            from Test test
            where test.topicId = :topicId
              and (test.status = :publishedStatus
                   or test.createdByUserId = :teacherId)
            """)
    Page<Test> findVisibleByTopicIdForTeacher(
            UUID topicId,
            UUID teacherId,
            @Param("publishedStatus") TestStatus publishedStatus,
            Pageable pageable
    );

    List<Test> findAllByTopicIdInAndStatus(Collection<UUID> topicIds, TestStatus status);

    @Query("""
            select test
            from Test test
            where test.topicId = :topicId
              and test.status = :status
              and exists (
                  select availability.id
                  from TestGroupAvailability availability
                  where availability.testId = test.id
                    and availability.groupId in :groupIds
                    and availability.visible = true
                    and (availability.availableFrom is null or availability.availableFrom <= :now)
                    and (availability.availableUntil is null or availability.availableUntil >= :now)
              )
            """)
    Page<Test> findAvailableByTopicIdForGroups(
            UUID topicId,
            TestStatus status,
            Collection<UUID> groupIds,
            Instant now,
            Pageable pageable
    );

    @Query("""
            select distinct test
            from Test test
            where test.topicId in :topicIds
              and test.status = :status
              and exists (
                  select availability.id
                  from TestGroupAvailability availability
                  where availability.testId = test.id
                    and availability.groupId in :groupIds
                    and availability.visible = true
                    and (availability.availableFrom is null or availability.availableFrom <= :now)
                    and (availability.availableUntil is null or availability.availableUntil >= :now)
              )
            """)
    List<Test> findAvailableByTopicIdInForGroups(
            Collection<UUID> topicIds,
            TestStatus status,
            Collection<UUID> groupIds,
            Instant now
    );

    List<Test> findAllByCreatedByUserIdAndStatusInOrderByAvailableUntilAscUpdatedAtDesc(
            UUID createdByUserId,
            Collection<TestStatus> statuses
    );

    long countByStatusAndAvailableUntilAfter(TestStatus status, Instant availableUntil);
}
