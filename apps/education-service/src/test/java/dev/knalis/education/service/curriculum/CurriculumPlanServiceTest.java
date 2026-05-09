package dev.knalis.education.service.curriculum;

import dev.knalis.education.dto.request.CreateCurriculumPlanRequest;
import dev.knalis.education.entity.CurriculumPlan;
import dev.knalis.education.entity.Specialty;
import dev.knalis.education.exception.CurriculumPlanAlreadyExistsException;
import dev.knalis.education.exception.CurriculumPlanInvalidCountsException;
import dev.knalis.education.repository.CurriculumPlanRepository;
import dev.knalis.education.repository.SpecialtyRepository;
import dev.knalis.education.repository.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurriculumPlanServiceTest {

    @Mock
    private CurriculumPlanRepository curriculumPlanRepository;

    @Mock
    private SpecialtyRepository specialtyRepository;

    @Mock
    private SubjectRepository subjectRepository;

    private CurriculumPlanService curriculumPlanService;

    @BeforeEach
    void setUp() {
        curriculumPlanService = new CurriculumPlanService(curriculumPlanRepository, specialtyRepository, subjectRepository);
    }

    @Test
    void createPlanRejectsInvalidCounts() {
        assertThrows(
                CurriculumPlanInvalidCountsException.class,
                () -> curriculumPlanService.createCurriculumPlan(
                        new CreateCurriculumPlanRequest(UUID.randomUUID(), 1, 1, UUID.randomUUID(), 0, 0, 0, false, false)
                )
        );
    }

    @Test
    void createPlanRejectsDuplicateActiveCombination() {
        UUID specialtyId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        Specialty specialty = new Specialty();
        specialty.setId(specialtyId);
        specialty.setActive(true);
        when(specialtyRepository.findById(specialtyId)).thenReturn(Optional.of(specialty));
        when(subjectRepository.existsById(subjectId)).thenReturn(true);
        when(curriculumPlanRepository.existsByActiveTrueAndSpecialtyIdAndStudyYearAndSemesterNumberAndSubjectId(
                specialtyId, 1, 1, subjectId
        )).thenReturn(true);

        assertThrows(
                CurriculumPlanAlreadyExistsException.class,
                () -> curriculumPlanService.createCurriculumPlan(
                        new CreateCurriculumPlanRequest(specialtyId, 1, 1, subjectId, 10, 5, 2, true, true)
                )
        );
    }
}
