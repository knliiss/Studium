package dev.knalis.education.repository;

import dev.knalis.education.entity.Subject;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select subject from Subject subject where subject.id = :id")
    Optional<Subject> findWithLockingById(@Param("id") UUID id);

    @Query("""
            select subject
            from Subject subject
            where subject.groupId = :groupId
               or exists (
                select 1
                from SubjectGroup subjectGroup
                where subjectGroup.subjectId = subject.id
                  and subjectGroup.groupId = :groupId
            )
            """)
    Page<Subject> findAllByBoundGroupId(@Param("groupId") UUID groupId, Pageable pageable);

    Page<Subject> findAllByNameContainingIgnoreCaseOrderByNameAsc(String name, Pageable pageable);

    @Query("""
            select subject
            from Subject subject
            where subject.groupId = :groupId
               or exists (
                select 1
                from SubjectGroup subjectGroup
                where subjectGroup.subjectId = subject.id
                  and subjectGroup.groupId = :groupId
            )
            order by subject.createdAt asc
            """)
    List<Subject> findAllByBoundGroupId(@Param("groupId") UUID groupId);
}
