package dev.knalis.education.controller;

import dev.knalis.education.dto.request.CreateGroupCurriculumOverrideRequest;
import dev.knalis.education.dto.request.UpdateGroupCurriculumOverrideRequest;
import dev.knalis.education.dto.response.GroupCurriculumOverrideResponse;
import dev.knalis.education.service.curriculum.GroupCurriculumOverrideService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/education/groups/{groupId}/curriculum-overrides")
@RequiredArgsConstructor
public class GroupCurriculumOverrideController {

    private final GroupCurriculumOverrideService overrideService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER','STUDENT')")
    public List<GroupCurriculumOverrideResponse> listOverrides(@PathVariable UUID groupId) {
        return overrideService.listGroupOverrides(groupId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public GroupCurriculumOverrideResponse createOverride(
            @PathVariable UUID groupId,
            @Valid @RequestBody CreateGroupCurriculumOverrideRequest request
    ) {
        return overrideService.createGroupOverride(groupId, request);
    }

    @PutMapping("/{overrideId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public GroupCurriculumOverrideResponse updateOverride(
            @PathVariable UUID groupId,
            @PathVariable UUID overrideId,
            @Valid @RequestBody UpdateGroupCurriculumOverrideRequest request
    ) {
        return overrideService.updateGroupOverride(groupId, overrideId, request);
    }

    @DeleteMapping("/{overrideId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public void deleteOverride(@PathVariable UUID groupId, @PathVariable UUID overrideId) {
        overrideService.deleteGroupOverride(groupId, overrideId);
    }
}
