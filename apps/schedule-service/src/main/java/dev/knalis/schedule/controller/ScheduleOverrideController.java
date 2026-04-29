package dev.knalis.schedule.controller;

import dev.knalis.schedule.dto.request.CreateScheduleOverrideRequest;
import dev.knalis.schedule.dto.request.UpdateScheduleOverrideRequest;
import dev.knalis.schedule.dto.response.ScheduleOverrideResponse;
import dev.knalis.schedule.service.override.ScheduleOverrideService;
import dev.knalis.shared.security.user.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/schedule/overrides")
@RequiredArgsConstructor
public class ScheduleOverrideController {
    
    private final ScheduleOverrideService scheduleOverrideService;
    private final CurrentUserService currentUserService;
    
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public ScheduleOverrideResponse createOverride(
            Authentication authentication,
            @Valid @RequestBody CreateScheduleOverrideRequest request
    ) {
        return scheduleOverrideService.createOverride(
                currentUserService.getCurrentUserId(authentication),
                hasAdminPrivileges(authentication),
                request
        );
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public ScheduleOverrideResponse updateOverride(
            Authentication authentication,
            @PathVariable("id") UUID overrideId,
            @Valid @RequestBody UpdateScheduleOverrideRequest request
    ) {
        return scheduleOverrideService.updateOverride(
                currentUserService.getCurrentUserId(authentication),
                hasAdminPrivileges(authentication),
                overrideId,
                request
        );
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public ResponseEntity<Void> deleteOverride(
            Authentication authentication,
            @PathVariable("id") UUID overrideId
    ) {
        scheduleOverrideService.deleteOverride(
                currentUserService.getCurrentUserId(authentication),
                hasAdminPrivileges(authentication),
                overrideId
        );
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/date/{date}")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public List<ScheduleOverrideResponse> getOverridesByDate(
            Authentication authentication,
            @PathVariable LocalDate date
    ) {
        return scheduleOverrideService.getOverridesByDate(
                date,
                currentUserService.getCurrentUserId(authentication),
                hasAdminPrivileges(authentication)
        );
    }
    
    private boolean hasAdminPrivileges(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> "ROLE_OWNER".equals(authority) || "ROLE_ADMIN".equals(authority));
    }
}
