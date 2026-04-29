package dev.knalis.education.controller;

import dev.knalis.education.dto.request.CreateGroupStudentRequest;
import dev.knalis.education.dto.request.CreateGroupRequest;
import dev.knalis.education.dto.request.UpdateGroupStudentRequest;
import dev.knalis.education.dto.response.GroupMembershipResponse;
import dev.knalis.education.dto.response.GroupPageResponse;
import dev.knalis.education.dto.response.GroupStudentMembershipResponse;
import dev.knalis.education.dto.response.GroupResponse;
import dev.knalis.education.service.group.GroupService;
import dev.knalis.shared.security.user.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/education/groups")
@RequiredArgsConstructor
public class GroupController {
    
    private final GroupService groupService;
    private final CurrentUserService currentUserService;
    
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public GroupResponse createGroup(Authentication authentication, @Valid @RequestBody CreateGroupRequest request) {
        return groupService.createGroup(currentUserService.getCurrentUserId(authentication), request);
    }

    @GetMapping
    public GroupPageResponse listGroups(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String q
    ) {
        return groupService.listGroups(page, size, sortBy, direction, q);
    }
    
    @GetMapping("/{id}")
    public GroupResponse getGroup(@PathVariable("id") UUID groupId) {
        return groupService.getGroup(groupId);
    }
    
    @GetMapping("/by-user/{userId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN') or authentication.name == #userId.toString()")
    public List<GroupMembershipResponse> getGroupsByUser(@PathVariable("userId") UUID userId) {
        return groupService.getGroupsByUser(userId);
    }

    @GetMapping("/{groupId}/students")
    public List<GroupStudentMembershipResponse> getGroupStudents(@PathVariable UUID groupId) {
        return groupService.getGroupStudents(groupId);
    }

    @PostMapping("/{groupId}/students")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public GroupStudentMembershipResponse addStudentToGroup(
            Authentication authentication,
            @PathVariable UUID groupId,
            @Valid @RequestBody CreateGroupStudentRequest request
    ) {
        return groupService.addStudentToGroup(currentUserService.getCurrentUserId(authentication), groupId, request);
    }

    @PatchMapping("/{groupId}/students/{userId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public GroupStudentMembershipResponse updateStudentInGroup(
            Authentication authentication,
            @PathVariable UUID groupId,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateGroupStudentRequest request
    ) {
        return groupService.updateStudentInGroup(
                currentUserService.getCurrentUserId(authentication),
                groupId,
                userId,
                request
        );
    }

    @DeleteMapping("/{groupId}/students/{userId}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public void removeStudentFromGroup(
            Authentication authentication,
            @PathVariable UUID groupId,
            @PathVariable UUID userId
    ) {
        groupService.removeStudentFromGroup(currentUserService.getCurrentUserId(authentication), groupId, userId);
    }
}
