package dev.knalis.gateway.client.education.dto;

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
