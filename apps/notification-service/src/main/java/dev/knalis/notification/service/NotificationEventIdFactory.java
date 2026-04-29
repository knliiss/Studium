package dev.knalis.notification.service;

import dev.knalis.notification.entity.NotificationType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Component
public class NotificationEventIdFactory {
    
    public UUID forRecipient(UUID eventId, UUID recipientUserId, NotificationType type) {
        return UUID.nameUUIDFromBytes((eventId + ":" + recipientUserId + ":" + type.name()).getBytes(StandardCharsets.UTF_8));
    }
    
    public UUID forReminder(
            UUID recipientUserId,
            NotificationType type,
            String relatedEntityType,
            UUID relatedEntityId,
            Instant reminderAt
    ) {
        return UUID.nameUUIDFromBytes((
                recipientUserId + ":" + type.name() + ":" + relatedEntityType + ":" + relatedEntityId + ":" + reminderAt
        ).getBytes(StandardCharsets.UTF_8));
    }
}
