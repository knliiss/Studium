package dev.knalis.education.service.specialty;

import dev.knalis.education.dto.request.CreateSpecialtyRequest;
import dev.knalis.education.dto.response.SpecialtyResponse;
import dev.knalis.education.entity.Specialty;
import dev.knalis.education.exception.SpecialtyCodeAlreadyExistsException;
import dev.knalis.education.exception.SpecialtyHasDependenciesException;
import dev.knalis.education.repository.CurriculumPlanRepository;
import dev.knalis.education.repository.GroupRepository;
import dev.knalis.education.repository.SpecialtyRepository;
import dev.knalis.education.repository.StreamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpecialtyServiceTest {

    @Mock
    private SpecialtyRepository specialtyRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private StreamRepository streamRepository;

    @Mock
    private CurriculumPlanRepository curriculumPlanRepository;

    private SpecialtyService specialtyService;

    @BeforeEach
    void setUp() {
        specialtyService = new SpecialtyService(
                specialtyRepository,
                groupRepository,
                streamRepository,
                curriculumPlanRepository
        );
    }

    @Test
    void createSpecialtyRejectsDuplicateCode() {
        when(specialtyRepository.existsByCodeIgnoreCase("CS")).thenReturn(true);
        assertThrows(
                SpecialtyCodeAlreadyExistsException.class,
                () -> specialtyService.createSpecialty(new CreateSpecialtyRequest("cs", "Computer Science", null))
        );
    }

    @Test
    void archiveSpecialtyRejectsDependencies() {
        UUID specialtyId = UUID.randomUUID();
        Specialty specialty = specialty(specialtyId);
        when(specialtyRepository.findById(specialtyId)).thenReturn(java.util.Optional.of(specialty));
        when(groupRepository.existsBySpecialtyId(specialtyId)).thenReturn(true);
        assertThrows(SpecialtyHasDependenciesException.class, () -> specialtyService.archiveSpecialty(specialtyId));
    }

    @Test
    void restoreSpecialtyActivatesEntity() {
        UUID specialtyId = UUID.randomUUID();
        Specialty specialty = specialty(specialtyId);
        specialty.setActive(false);
        when(specialtyRepository.findById(specialtyId)).thenReturn(java.util.Optional.of(specialty));
        when(specialtyRepository.save(any(Specialty.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SpecialtyResponse response = specialtyService.restoreSpecialty(specialtyId);

        assertEquals(true, response.active());
    }

    private Specialty specialty(UUID id) {
        Specialty specialty = new Specialty();
        specialty.setId(id);
        specialty.setCode("CS");
        specialty.setName("Computer Science");
        specialty.setActive(true);
        specialty.setCreatedAt(Instant.now());
        specialty.setUpdatedAt(Instant.now());
        return specialty;
    }
}
