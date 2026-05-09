package dev.knalis.education.repository;

import dev.knalis.education.entity.CurriculumPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CurriculumPlanRepository extends JpaRepository<CurriculumPlan, UUID> {

    boolean existsByActiveTrueAndSpecialtyIdAndStudyYearAndSemesterNumberAndSubjectId(
            UUID specialtyId,
            Integer studyYear,
            Integer semesterNumber,
            UUID subjectId
    );

    boolean existsByActiveTrueAndSpecialtyIdAndStudyYearAndSemesterNumberAndSubjectIdAndIdNot(
            UUID specialtyId,
            Integer studyYear,
            Integer semesterNumber,
            UUID subjectId,
            UUID id
    );

    boolean existsBySpecialtyId(UUID specialtyId);

    List<CurriculumPlan> findAllByOrderByCreatedAtDesc();

    List<CurriculumPlan> findAllBySpecialtyIdOrderByCreatedAtDesc(UUID specialtyId);

    List<CurriculumPlan> findAllByStudyYearOrderByCreatedAtDesc(Integer studyYear);

    List<CurriculumPlan> findAllBySemesterNumberOrderByCreatedAtDesc(Integer semesterNumber);

    List<CurriculumPlan> findAllBySubjectIdOrderByCreatedAtDesc(UUID subjectId);

    List<CurriculumPlan> findAllByActiveOrderByCreatedAtDesc(boolean active);

    List<CurriculumPlan> findAllBySpecialtyIdAndStudyYearAndActiveTrue(UUID specialtyId, Integer studyYear);

    List<CurriculumPlan> findAllBySpecialtyIdAndStudyYearAndSemesterNumberAndActiveTrue(
            UUID specialtyId,
            Integer studyYear,
            Integer semesterNumber
    );

    List<CurriculumPlan> findAllBySpecialtyIdAndStudyYearAndActiveTrueAndSubjectIdIn(
            UUID specialtyId,
            Integer studyYear,
            Collection<UUID> subjectIds
    );
}
