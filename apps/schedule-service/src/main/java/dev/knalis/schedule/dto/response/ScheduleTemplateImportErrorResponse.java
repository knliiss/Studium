package dev.knalis.schedule.dto.response;

public record ScheduleTemplateImportErrorResponse(
        int index,
        String code,
        String message
) {
}
