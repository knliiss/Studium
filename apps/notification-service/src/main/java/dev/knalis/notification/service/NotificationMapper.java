package dev.knalis.notification.service;

import dev.knalis.notification.dto.response.NotificationResponse;
import dev.knalis.notification.entity.Notification;
import dev.knalis.notification.entity.NotificationStatus;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {
    
    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getUserId(),
                notification.getType(),
                notification.getCategory(),
                notification.getTitle(),
                notification.getBody(),
                notification.getPayloadJson(),
                notification.getStatus() == NotificationStatus.READ,
                notification.getStatus(),
                notification.getSourceEventId(),
                notification.getSourceEventType(),
                notification.getCreatedAt(),
                notification.getUpdatedAt(),
                notification.getReadAt()
        );
    }
}
