package dev.knalis.schedule.dto.response;

import java.util.List;

public record ScheduleConflictCheckResponse(
        boolean hasConflicts,
        List<ScheduleConflictItemResponse> conflicts
) {
}
