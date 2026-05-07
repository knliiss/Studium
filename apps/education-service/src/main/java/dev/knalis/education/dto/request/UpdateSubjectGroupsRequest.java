package dev.knalis.education.dto.request;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UpdateSubjectGroupsRequest(
        @Size(max = 100)
        List<UUID> groupIds
) {
}
