package dev.knalis.education.repository;

import dev.knalis.education.entity.Subject;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    Page<Subject> findAllByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Subject> findAllByNameContainingIgnoreCaseOrderByNameAsc(String name, Pageable pageable);

    boolean existsByNameIgnoreCase(String name);

    @Query("""
            select count(subject) > 0
            from Subject subject
            where lower(subject.name) = lower(:name)
              and subject.id <> :subjectId
            """)
    boolean existsByNameIgnoreCaseAndIdNot(@Param("name") String name, @Param("subjectId") UUID subjectId);

    @Query("""
            select subject
            from Subject subject
            where lower(subject.name) like lower(concat('%', :name, '%'))
              and exists (
                select 1
                from SubjectTeacher subjectTeacher
                where subjectTeacher.subjectId = subject.id
                  and subjectTeacher.teacherId = :teacherId
              )
            """)
    Page<Subject> findAllByTeacherIdAndNameContainingIgnoreCase(
            @Param("teacherId") UUID teacherId,
            @Param("name") String name,
            Pageable pageable
    );

    @Query("""
            select subject
            from Subject subject
            where lower(subject.name) like lower(concat('%', :name, '%'))
              and (
                subject.groupId in :groupIds
                or exists (
                    select 1
                    from SubjectGroup subjectGroup
                    where subjectGroup.subjectId = subject.id
                      and subjectGroup.groupId in :groupIds
                )
              )
            """)
    Page<Subject> findAllByBoundGroupIdsAndNameContainingIgnoreCase(
            @Param("groupIds") Collection<UUID> groupIds,
            @Param("name") String name,
            Pageable pageable
    );

    @Query("""
            select count(subject) > 0
            from Subject subject
            where subject.id = :subjectId
              and exists (
                select 1
                from SubjectTeacher subjectTeacher
                where subjectTeacher.subjectId = subject.id
                  and subjectTeacher.teacherId = :teacherId
              )
            """)
    boolean existsByIdAndTeacherId(@Param("subjectId") UUID subjectId, @Param("teacherId") UUID teacherId);

    @Query("""
            select count(subject) > 0
            from Subject subject
            where subject.id = :subjectId
              and (
                subject.groupId in :groupIds
                or exists (
                    select 1
                    from SubjectGroup subjectGroup
                    where subjectGroup.subjectId = subject.id
                      and subjectGroup.groupId in :groupIds
                )
              )
            """)
    boolean existsByIdAndBoundGroupIds(
            @Param("subjectId") UUID subjectId,
            @Param("groupIds") Collection<UUID> groupIds
    );

    @Query("""
            select count(subject) > 0
            from Subject subject
            where (subject.groupId = :groupId
                    or exists (
                        select 1
                        from SubjectGroup subjectGroup
                        where subjectGroup.subjectId = subject.id
                          and subjectGroup.groupId = :groupId
                    ))
              and exists (
                    select 1
                    from SubjectTeacher subjectTeacher
                    where subjectTeacher.subjectId = subject.id
                      and subjectTeacher.teacherId = :teacherId
              )
            """)
    boolean existsByTeacherIdAndBoundGroupId(@Param("teacherId") UUID teacherId, @Param("groupId") UUID groupId);

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
