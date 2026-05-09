package dev.knalis.schedule.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateRoomCapabilitiesRequest(
        @NotNull
        @Size(max = 10)
        List<@Valid UpsertRoomCapabilityRequest> capabilities
) {
}
