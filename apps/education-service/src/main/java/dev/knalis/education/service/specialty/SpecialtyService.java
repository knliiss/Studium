package dev.knalis.education.service.specialty;

import dev.knalis.education.dto.request.CreateSpecialtyRequest;
import dev.knalis.education.dto.request.UpdateSpecialtyRequest;
import dev.knalis.education.dto.response.SpecialtyResponse;
import dev.knalis.education.entity.Specialty;
import dev.knalis.education.exception.SpecialtyCodeAlreadyExistsException;
import dev.knalis.education.exception.SpecialtyHasDependenciesException;
import dev.knalis.education.exception.SpecialtyNotFoundException;
import dev.knalis.education.repository.CurriculumPlanRepository;
import dev.knalis.education.repository.GroupRepository;
import dev.knalis.education.repository.SpecialtyRepository;
import dev.knalis.education.repository.StreamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpecialtyService {

    private final SpecialtyRepository specialtyRepository;
    private final GroupRepository groupRepository;
    private final StreamRepository streamRepository;
    private final CurriculumPlanRepository curriculumPlanRepository;

    @Transactional
    public SpecialtyResponse createSpecialty(CreateSpecialtyRequest request) {
        String code = normalizeCode(request.code());
        if (specialtyRepository.existsByCodeIgnoreCase(code)) {
            throw new SpecialtyCodeAlreadyExistsException(code);
        }
        Specialty specialty = new Specialty();
        specialty.setCode(code);
        specialty.setName(request.name().trim());
        specialty.setDescription(normalizeDescription(request.description()));
        specialty.setActive(true);
        return toResponse(specialtyRepository.save(specialty));
    }

    @Transactional
    public SpecialtyResponse updateSpecialty(UUID specialtyId, UpdateSpecialtyRequest request) {
        Specialty specialty = specialtyRepository.findById(specialtyId)
                .orElseThrow(() -> new SpecialtyNotFoundException(specialtyId));
        String code = normalizeCode(request.code());
        if (specialtyRepository.existsByCodeIgnoreCaseAndIdNot(code, specialtyId)) {
            throw new SpecialtyCodeAlreadyExistsException(code);
        }
        specialty.setCode(code);
        specialty.setName(request.name().trim());
        specialty.setDescription(normalizeDescription(request.description()));
        return toResponse(specialtyRepository.save(specialty));
    }

    @Transactional(readOnly = true)
    public SpecialtyResponse getSpecialty(UUID specialtyId) {
        Specialty specialty = specialtyRepository.findById(specialtyId)
                .orElseThrow(() -> new SpecialtyNotFoundException(specialtyId));
        return toResponse(specialty);
    }

    @Transactional(readOnly = true)
    public List<SpecialtyResponse> listSpecialties(Boolean active) {
        List<Specialty> specialties;
        if (active == null) {
            specialties = specialtyRepository.findAllByOrderByCodeAsc();
        } else if (active) {
            specialties = specialtyRepository.findAllByActiveTrueOrderByCodeAsc();
        } else {
            specialties = specialtyRepository.findAllByOrderByCodeAsc().stream()
                    .filter(specialty -> !specialty.isActive())
                    .toList();
        }
        return specialties.stream().map(this::toResponse).toList();
    }

    @Transactional
    public SpecialtyResponse archiveSpecialty(UUID specialtyId) {
        Specialty specialty = specialtyRepository.findById(specialtyId)
                .orElseThrow(() -> new SpecialtyNotFoundException(specialtyId));
        if (groupRepository.existsBySpecialtyId(specialtyId)) {
            throw new SpecialtyHasDependenciesException(specialtyId, "GROUPS");
        }
        if (streamRepository.existsBySpecialtyId(specialtyId)) {
            throw new SpecialtyHasDependenciesException(specialtyId, "STREAMS");
        }
        if (curriculumPlanRepository.existsBySpecialtyId(specialtyId)) {
            throw new SpecialtyHasDependenciesException(specialtyId, "CURRICULUM_PLANS");
        }
        specialty.setActive(false);
        return toResponse(specialtyRepository.save(specialty));
    }

    @Transactional
    public SpecialtyResponse restoreSpecialty(UUID specialtyId) {
        Specialty specialty = specialtyRepository.findById(specialtyId)
                .orElseThrow(() -> new SpecialtyNotFoundException(specialtyId));
        specialty.setActive(true);
        return toResponse(specialtyRepository.save(specialty));
    }

    private SpecialtyResponse toResponse(Specialty specialty) {
        return new SpecialtyResponse(
                specialty.getId(),
                specialty.getCode(),
                specialty.getName(),
                specialty.getDescription(),
                specialty.isActive(),
                specialty.getCreatedAt(),
                specialty.getUpdatedAt()
        );
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase();
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}
