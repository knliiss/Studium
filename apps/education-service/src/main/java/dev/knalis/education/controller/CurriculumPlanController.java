package dev.knalis.education.controller;

import dev.knalis.education.dto.request.CreateCurriculumPlanRequest;
import dev.knalis.education.dto.request.UpdateCurriculumPlanRequest;
import dev.knalis.education.dto.response.CurriculumPlanResponse;
import dev.knalis.education.service.curriculum.CurriculumPlanService;
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
@RequestMapping("/api/v1/education/curriculum-plans")
@RequiredArgsConstructor
public class CurriculumPlanController {

    private final CurriculumPlanService curriculumPlanService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public List<CurriculumPlanResponse> listPlans(
            @RequestParam(required = false) UUID specialtyId,
            @RequestParam(required = false) Integer studyYear,
            @RequestParam(required = false) Integer semesterNumber,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) Boolean active
    ) {
        return curriculumPlanService.listCurriculumPlans(specialtyId, studyYear, semesterNumber, subjectId, active);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public CurriculumPlanResponse getPlan(@PathVariable("id") UUID curriculumPlanId) {
        return curriculumPlanService.getCurriculumPlan(curriculumPlanId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public CurriculumPlanResponse createPlan(@Valid @RequestBody CreateCurriculumPlanRequest request) {
        return curriculumPlanService.createCurriculumPlan(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public CurriculumPlanResponse updatePlan(
            @PathVariable("id") UUID curriculumPlanId,
            @Valid @RequestBody UpdateCurriculumPlanRequest request
    ) {
        return curriculumPlanService.updateCurriculumPlan(curriculumPlanId, request);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public CurriculumPlanResponse archivePlan(@PathVariable("id") UUID curriculumPlanId) {
        return curriculumPlanService.archiveCurriculumPlan(curriculumPlanId);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public CurriculumPlanResponse restorePlan(@PathVariable("id") UUID curriculumPlanId) {
        return curriculumPlanService.restoreCurriculumPlan(curriculumPlanId);
    }
}
