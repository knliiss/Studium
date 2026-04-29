package dev.knalis.schedule.dto.response;

import java.util.List;

public record ScheduleTemplateImportResponse(
        int totalItems,
        int createdCount,
        int skippedCount,
        int failedCount,
        List<ScheduleTemplateImportErrorResponse> errors,
        List<ScheduleConflictItemResponse> conflicts
) {
}
