package dev.knalis.schedule.controller;

import dev.knalis.schedule.dto.request.CreateAcademicSemesterRequest;
import dev.knalis.schedule.dto.request.UpdateAcademicSemesterRequest;
import dev.knalis.schedule.dto.response.AcademicSemesterResponse;
import dev.knalis.schedule.service.semester.AcademicSemesterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/schedule/semesters")
@RequiredArgsConstructor
public class AcademicSemesterController {
    
    private final AcademicSemesterService academicSemesterService;
    
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public AcademicSemesterResponse createSemester(@Valid @RequestBody CreateAcademicSemesterRequest request) {
        return academicSemesterService.createSemester(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public List<AcademicSemesterResponse> listSemesters() {
        return academicSemesterService.listSemesters();
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public AcademicSemesterResponse getSemester(@PathVariable("id") UUID semesterId) {
        return academicSemesterService.getSemester(semesterId);
    }
    
    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public AcademicSemesterResponse getActiveSemester() {
        return academicSemesterService.getActiveSemester();
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public AcademicSemesterResponse updateSemester(
            @PathVariable("id") UUID semesterId,
            @Valid @RequestBody UpdateAcademicSemesterRequest request
    ) {
        return academicSemesterService.updateSemester(semesterId, request);
    }
}
