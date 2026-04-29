package dev.knalis.education.repository;

import dev.knalis.education.entity.Subject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {

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
