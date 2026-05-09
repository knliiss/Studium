package dev.knalis.education.controller;

import dev.knalis.education.dto.request.CreateSpecialtyRequest;
import dev.knalis.education.dto.request.UpdateSpecialtyRequest;
import dev.knalis.education.dto.response.SpecialtyResponse;
import dev.knalis.education.service.specialty.SpecialtyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/education/specialties")
@RequiredArgsConstructor
public class SpecialtyController {

    private final SpecialtyService specialtyService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public List<SpecialtyResponse> listSpecialties(@RequestParam(required = false) Boolean active) {
        return specialtyService.listSpecialties(active);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public SpecialtyResponse getSpecialty(@PathVariable("id") UUID specialtyId) {
        return specialtyService.getSpecialty(specialtyId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public SpecialtyResponse createSpecialty(@Valid @RequestBody CreateSpecialtyRequest request) {
        return specialtyService.createSpecialty(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public SpecialtyResponse updateSpecialty(
            @PathVariable("id") UUID specialtyId,
            @Valid @RequestBody UpdateSpecialtyRequest request
    ) {
        return specialtyService.updateSpecialty(specialtyId, request);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public SpecialtyResponse archiveSpecialty(@PathVariable("id") UUID specialtyId) {
        return specialtyService.archiveSpecialty(specialtyId);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public SpecialtyResponse restoreSpecialty(@PathVariable("id") UUID specialtyId) {
        return specialtyService.restoreSpecialty(specialtyId);
    }
}
