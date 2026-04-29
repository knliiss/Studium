package dev.knalis.gateway.dto;

import java.util.Map;
import java.util.UUID;

public record SearchItemResponse(
        String type,
        UUID id,
        String title,
        String subtitle,
        String sourceService,
        Map<String, Object> targetMetadata
) {
}
