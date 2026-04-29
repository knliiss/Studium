package dev.knalis.gateway.client.testing.dto;

import java.util.Map;
import java.util.UUID;

public record SearchItemResponse(
        String type,
        UUID id,
        String title,
        String subtitle,
        Map<String, Object> targetMetadata
) {
}
