package dev.knalis.shared.web.dto;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String errorCode,
        String message,
        String path,
        String requestId,
        Map<String, Object> details
) {
}