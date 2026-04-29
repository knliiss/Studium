package dev.knalis.notification.service;

import dev.knalis.contracts.event.UserBannedEvent;
import dev.knalis.contracts.event.UserEmailChangedEvent;
import dev.knalis.contracts.event.UserRegisteredEvent;
import dev.knalis.contracts.event.UserUnbannedEvent;
import dev.knalis.contracts.event.UserUsernameChangedEvent;
import dev.knalis.notification.entity.NotificationCategory;
import dev.knalis.notification.entity.NotificationType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class AuthNotificationFactory {
    
    public NotificationDraft fromUserRegistered(UserRegisteredEvent event) {
        return new NotificationDraft(
                event.userId(),
                event.eventId(),
                event.getClass().getSimpleName(),
                NotificationType.WELCOME,
                NotificationCategory.ACCOUNT,
                "Welcome",
                "Your account was created successfully.",
                Map.of(
                        "username", event.username(),
                        "email", event.email()
                )
        );
    }
    
    public NotificationDraft fromUserEmailChanged(UserEmailChangedEvent event) {
        return new NotificationDraft(
                event.userId(),
                event.eventId(),
                event.getClass().getSimpleName(),
                NotificationType.EMAIL_CHANGED,
                NotificationCategory.ACCOUNT,
                "Email changed",
                "Your account email address was updated.",
                Map.of(
                        "oldEmail", event.oldEmail(),
                        "newEmail", event.newEmail()
                )
        );
    }
    
    public NotificationDraft fromUserUsernameChanged(UserUsernameChangedEvent event) {
        return new NotificationDraft(
                event.userId(),
                event.eventId(),
                event.getClass().getSimpleName(),
                NotificationType.USERNAME_CHANGED,
                NotificationCategory.ACCOUNT,
                "Username changed",
                "Your account username was updated.",
                Map.of(
                        "oldUsername", event.oldUsername(),
                        "newUsername", event.newUsername()
                )
        );
    }
    
    public NotificationDraft fromUserBanned(UserBannedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reason", event.reason());
        payload.put("expiresAt", event.expiresAt());
        payload.put("bannedBy", event.bannedByUsername());
        
        return new NotificationDraft(
                event.userId(),
                event.eventId(),
                event.getClass().getSimpleName(),
                NotificationType.USER_BANNED,
                NotificationCategory.SECURITY,
                "Account restricted",
                event.expiresAt() == null
                        ? "Your account has been suspended."
                        : "Your account has been suspended until " + event.expiresAt() + ".",
                payload
        );
    }
    
    public NotificationDraft fromUserUnbanned(UserUnbannedEvent event) {
        return new NotificationDraft(
                event.userId(),
                event.eventId(),
                event.getClass().getSimpleName(),
                NotificationType.USER_UNBANNED,
                NotificationCategory.SECURITY,
                "Account restored",
                "Your account access has been restored.",
                Map.of("unbannedBy", event.unbannedByUsername())
        );
    }
}
