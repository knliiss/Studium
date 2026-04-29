package dev.knalis.file.dto.request;

import jakarta.validation.constraints.NotNull;

public record FileScanResultRequest(
        @NotNull Boolean clean,
        String message
) {
}
