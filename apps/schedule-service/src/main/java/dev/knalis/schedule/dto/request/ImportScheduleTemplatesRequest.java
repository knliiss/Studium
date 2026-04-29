package dev.knalis.schedule.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ImportScheduleTemplatesRequest(
        
        @NotEmpty
        List<@Valid CreateScheduleTemplateRequest> items
) {
}
