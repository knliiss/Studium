package dev.knalis.education.service.curriculum;

import dev.knalis.education.dto.request.CreateCurriculumPlanRequest;
import dev.knalis.education.dto.request.UpdateCurriculumPlanRequest;
import dev.knalis.education.dto.response.CurriculumPlanResponse;
import dev.knalis.education.entity.CurriculumPlan;
import dev.knalis.education.entity.Specialty;
import dev.knalis.education.exception.CurriculumPlanAlreadyExistsException;
import dev.knalis.education.exception.CurriculumPlanInvalidCountsException;
import dev.knalis.education.exception.CurriculumPlanNotFoundException;
import dev.knalis.education.exception.SpecialtyNotActiveException;
import dev.knalis.education.exception.SpecialtyNotFoundException;
import dev.knalis.education.exception.SubjectNotFoundException;
import dev.knalis.education.repository.CurriculumPlanRepository;
import dev.knalis.education.repository.SpecialtyRepository;
import dev.knalis.education.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CurriculumPlanService {

    private final CurriculumPlanRepository curriculumPlanRepository;
    private final SpecialtyRepository specialtyRepository;
    private final SubjectRepository subjectRepository;

    @Transactional
    public CurriculumPlanResponse createCurriculumPlan(CreateCurriculumPlanRequest request) {
        validateCounts(request.lectureCount(), request.practiceCount(), request.labCount());
        ensureSpecialtyActive(request.specialtyId());
        ensureSubjectExists(request.subjectId());
        if (curriculumPlanRepository.existsByActiveTrueAndSpecialtyIdAndStudyYearAndSemesterNumberAndSubjectId(
                request.specialtyId(),
                request.studyYear(),
                request.semesterNumber(),
                request.subjectId()
        )) {
            throw new CurriculumPlanAlreadyExistsException(
                    request.specialtyId(),
                    request.studyYear(),
                    request.semesterNumber(),
                    request.subjectId()
            );
        }
        CurriculumPlan plan = new CurriculumPlan();
        applyUpdate(plan, request.specialtyId(), request.studyYear(), request.semesterNumber(), request.subjectId(),
                request.lectureCount(), request.practiceCount(), request.labCount(),
                request.supportsStreamLecture(), request.requiresSubgroupsForLabs());
        plan.setActive(true);
        return toResponse(curriculumPlanRepository.save(plan));
    }

    @Transactional
    public CurriculumPlanResponse updateCurriculumPlan(UUID curriculumPlanId, UpdateCurriculumPlanRequest request) {
        CurriculumPlan plan = curriculumPlanRepository.findById(curriculumPlanId)
                .orElseThrow(() -> new CurriculumPlanNotFoundException(curriculumPlanId));
        validateCounts(request.lectureCount(), request.practiceCount(), request.labCount());
        ensureSpecialtyActive(request.specialtyId());
        ensureSubjectExists(request.subjectId());
        if (curriculumPlanRepository.existsByActiveTrueAndSpecialtyIdAndStudyYearAndSemesterNumberAndSubjectIdAndIdNot(
                request.specialtyId(),
                request.studyYear(),
                request.semesterNumber(),
                request.subjectId(),
                curriculumPlanId
        )) {
            throw new CurriculumPlanAlreadyExistsException(
                    request.specialtyId(),
                    request.studyYear(),
                    request.semesterNumber(),
                    request.subjectId()
            );
        }
        applyUpdate(plan, request.specialtyId(), request.studyYear(), request.semesterNumber(), request.subjectId(),
                request.lectureCount(), request.practiceCount(), request.labCount(),
                request.supportsStreamLecture(), request.requiresSubgroupsForLabs());
        return toResponse(curriculumPlanRepository.save(plan));
    }

    @Transactional(readOnly = true)
    public CurriculumPlanResponse getCurriculumPlan(UUID curriculumPlanId) {
        CurriculumPlan plan = curriculumPlanRepository.findById(curriculumPlanId)
                .orElseThrow(() -> new CurriculumPlanNotFoundException(curriculumPlanId));
        return toResponse(plan);
    }

    @Transactional(readOnly = true)
    public List<CurriculumPlanResponse> listCurriculumPlans(
            UUID specialtyId,
            Integer studyYear,
            Integer semesterNumber,
            UUID subjectId,
            Boolean active
    ) {
        return curriculumPlanRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(plan -> specialtyId == null || specialtyId.equals(plan.getSpecialtyId()))
                .filter(plan -> studyYear == null || studyYear.equals(plan.getStudyYear()))
                .filter(plan -> semesterNumber == null || semesterNumber.equals(plan.getSemesterNumber()))
                .filter(plan -> subjectId == null || subjectId.equals(plan.getSubjectId()))
                .filter(plan -> active == null || active.equals(plan.isActive()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CurriculumPlanResponse archiveCurriculumPlan(UUID curriculumPlanId) {
        CurriculumPlan plan = curriculumPlanRepository.findById(curriculumPlanId)
                .orElseThrow(() -> new CurriculumPlanNotFoundException(curriculumPlanId));
        plan.setActive(false);
        return toResponse(curriculumPlanRepository.save(plan));
    }

    @Transactional
    public CurriculumPlanResponse restoreCurriculumPlan(UUID curriculumPlanId) {
        CurriculumPlan plan = curriculumPlanRepository.findById(curriculumPlanId)
                .orElseThrow(() -> new CurriculumPlanNotFoundException(curriculumPlanId));
        ensureSpecialtyActive(plan.getSpecialtyId());
        if (curriculumPlanRepository.existsByActiveTrueAndSpecialtyIdAndStudyYearAndSemesterNumberAndSubjectIdAndIdNot(
                plan.getSpecialtyId(),
                plan.getStudyYear(),
                plan.getSemesterNumber(),
                plan.getSubjectId(),
                curriculumPlanId
        )) {
            throw new CurriculumPlanAlreadyExistsException(
                    plan.getSpecialtyId(),
                    plan.getStudyYear(),
                    plan.getSemesterNumber(),
                    plan.getSubjectId()
            );
        }
        plan.setActive(true);
        return toResponse(curriculumPlanRepository.save(plan));
    }

    private void applyUpdate(
            CurriculumPlan plan,
            UUID specialtyId,
            Integer studyYear,
            Integer semesterNumber,
            UUID subjectId,
            Integer lectureCount,
            Integer practiceCount,
            Integer labCount,
            boolean supportsStreamLecture,
            boolean requiresSubgroupsForLabs
    ) {
        plan.setSpecialtyId(specialtyId);
        plan.setStudyYear(studyYear);
        plan.setSemesterNumber(semesterNumber);
        plan.setSubjectId(subjectId);
        plan.setLectureCount(lectureCount);
        plan.setPracticeCount(practiceCount);
        plan.setLabCount(labCount);
        plan.setSupportsStreamLecture(supportsStreamLecture);
        plan.setRequiresSubgroupsForLabs(requiresSubgroupsForLabs);
    }

    private void validateCounts(Integer lectureCount, Integer practiceCount, Integer labCount) {
        if (lectureCount < 0 || practiceCount < 0 || labCount < 0
                || (lectureCount == 0 && practiceCount == 0 && labCount == 0)) {
            throw new CurriculumPlanInvalidCountsException(lectureCount, practiceCount, labCount);
        }
    }

    private void ensureSpecialtyActive(UUID specialtyId) {
        Specialty specialty = specialtyRepository.findById(specialtyId)
                .orElseThrow(() -> new SpecialtyNotFoundException(specialtyId));
        if (!specialty.isActive()) {
            throw new SpecialtyNotActiveException(specialtyId);
        }
    }

    private void ensureSubjectExists(UUID subjectId) {
        if (!subjectRepository.existsById(subjectId)) {
            throw new SubjectNotFoundException(subjectId);
        }
    }

    private CurriculumPlanResponse toResponse(CurriculumPlan plan) {
        return new CurriculumPlanResponse(
                plan.getId(),
                plan.getSpecialtyId(),
                plan.getStudyYear(),
                plan.getSemesterNumber(),
                plan.getSubjectId(),
                plan.getLectureCount(),
                plan.getPracticeCount(),
                plan.getLabCount(),
                plan.isSupportsStreamLecture(),
                plan.isRequiresSubgroupsForLabs(),
                plan.isActive(),
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }
}
