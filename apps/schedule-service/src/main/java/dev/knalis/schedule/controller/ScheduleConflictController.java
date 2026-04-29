package dev.knalis.schedule.controller;

import dev.knalis.schedule.dto.request.ScheduleConflictCheckRequest;
import dev.knalis.schedule.dto.response.ScheduleConflictCheckResponse;
import dev.knalis.schedule.service.schedule.ScheduleConflictPreviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedule/conflicts")
@RequiredArgsConstructor
public class ScheduleConflictController {

    private final ScheduleConflictPreviewService scheduleConflictPreviewService;

    @PostMapping("/check")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','TEACHER')")
    public ScheduleConflictCheckResponse check(@Valid @RequestBody ScheduleConflictCheckRequest request) {
        return scheduleConflictPreviewService.check(request);
    }
}
