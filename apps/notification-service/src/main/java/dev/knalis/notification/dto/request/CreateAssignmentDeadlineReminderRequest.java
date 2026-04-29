package dev.knalis.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateAssignmentDeadlineReminderRequest(
        @NotNull UUID assignmentId,
        @NotBlank String title,
        @NotNull Instant deadline,
        @NotNull Instant reminderAt
) {
}
