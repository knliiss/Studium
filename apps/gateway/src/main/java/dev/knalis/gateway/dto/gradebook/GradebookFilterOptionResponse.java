package dev.knalis.gateway.dto.gradebook;

import java.util.UUID;

public record GradebookFilterOptionResponse(
        UUID id,
        String label
) {
}
