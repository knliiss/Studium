package dev.knalis.assignment.client.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record CreateAssignmentDeadlineReminderRequest(
        UUID assignmentId,
        String title,
        Instant deadline,
        Instant reminderAt
) {
}
