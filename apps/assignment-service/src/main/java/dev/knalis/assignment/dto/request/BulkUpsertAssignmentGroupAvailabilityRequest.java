package dev.knalis.assignment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkUpsertAssignmentGroupAvailabilityRequest(

        @NotEmpty
        @Size(max = 100)
        List<@Valid UpsertAssignmentGroupAvailabilityRequest> items
) {
}
