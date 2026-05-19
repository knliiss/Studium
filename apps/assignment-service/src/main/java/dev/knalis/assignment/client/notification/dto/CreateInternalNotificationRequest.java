package dev.knalis.assignment.client.notification.dto;

import java.util.Map;

public record CreateInternalNotificationRequest(
        String type,
        String category,
        String title,
        String body,
        Map<String, Object> payload
) {
}
