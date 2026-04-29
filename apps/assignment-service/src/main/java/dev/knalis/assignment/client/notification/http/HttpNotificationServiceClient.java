package dev.knalis.assignment.client.notification.http;

import dev.knalis.assignment.client.notification.NotificationServiceClient;
import dev.knalis.assignment.client.notification.dto.CreateAssignmentDeadlineReminderRequest;
import dev.knalis.assignment.config.AssignmentNotificationServiceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HttpNotificationServiceClient implements NotificationServiceClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final RestClient notificationServiceRestClient;
    private final AssignmentNotificationServiceProperties properties;

    @Override
    public void createAssignmentDeadlineReminder(
            UUID userId,
            UUID assignmentId,
            String title,
            Instant deadline,
            Instant reminderAt
    ) {
        try {
            notificationServiceRestClient.post()
                    .uri("/internal/notifications/users/{userId}/reminders/assignments/deadline", userId)
                    .header(INTERNAL_SECRET_HEADER, properties.getSharedSecret())
                    .body(new CreateAssignmentDeadlineReminderRequest(
                            assignmentId,
                            title,
                            deadline,
                            reminderAt
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException("Failed to create assignment deadline reminder for userId=" + userId, exception);
        }
    }
}
