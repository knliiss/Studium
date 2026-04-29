package dev.knalis.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateTestDeadlineReminderRequest(
        @NotNull UUID testId,
        @NotBlank String title,
        @NotNull Instant deadline,
        @NotNull Instant reminderAt
) {
}
