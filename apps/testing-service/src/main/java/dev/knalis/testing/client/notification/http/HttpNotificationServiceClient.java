package dev.knalis.testing.client.notification.http;

import dev.knalis.testing.client.notification.NotificationServiceClient;
import dev.knalis.testing.client.notification.dto.CreateTestDeadlineReminderRequest;
import dev.knalis.testing.config.TestingNotificationServiceProperties;
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
    private final TestingNotificationServiceProperties properties;

    @Override
    public void createTestDeadlineReminder(
            UUID userId,
            UUID testId,
            String title,
            Instant deadline,
            Instant reminderAt
    ) {
        try {
            notificationServiceRestClient.post()
                    .uri("/internal/notifications/users/{userId}/reminders/tests/deadline", userId)
                    .header(INTERNAL_SECRET_HEADER, properties.getSharedSecret())
                    .body(new CreateTestDeadlineReminderRequest(
                            testId,
                            title,
                            deadline,
                            reminderAt
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException("Failed to create test deadline reminder for userId=" + userId, exception);
        }
    }
}
