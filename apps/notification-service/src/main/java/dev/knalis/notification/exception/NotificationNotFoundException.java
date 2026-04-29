package dev.knalis.notification.exception;

import dev.knalis.shared.web.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class NotificationNotFoundException extends AppException {
    
    public NotificationNotFoundException(UUID notificationId) {
        super(
                HttpStatus.NOT_FOUND,
                "NOTIFICATION_NOT_FOUND",
                "Notification was not found",
                Map.of("notificationId", notificationId)
        );
    }
}
