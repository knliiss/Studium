package dev.knalis.education.repository;

import dev.knalis.education.entity.SubjectTeacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubjectTeacherRepository extends JpaRepository<SubjectTeacher, UUID> {

    List<SubjectTeacher> findAllBySubjectIdOrderByCreatedAtAsc(UUID subjectId);

    boolean existsBySubjectIdAndTeacherId(UUID subjectId, UUID teacherId);

    Optional<SubjectTeacher> findBySubjectIdAndTeacherId(UUID subjectId, UUID teacherId);

    void deleteAllBySubjectId(UUID subjectId);
}
