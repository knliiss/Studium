package dev.knalis.notification.messaging;

import dev.knalis.contracts.event.UserBannedEvent;
import dev.knalis.contracts.event.UserEmailChangedEvent;
import dev.knalis.contracts.event.UserRegisteredEvent;
import dev.knalis.contracts.event.UserUnbannedEvent;
import dev.knalis.contracts.event.UserUsernameChangedEvent;
import dev.knalis.notification.service.AuthNotificationFactory;
import dev.knalis.notification.service.NotificationDraft;
import dev.knalis.notification.service.NotificationService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthNotificationEventsListener {
    
    private final NotificationService notificationService;
    private final AuthNotificationFactory authNotificationFactory;
    private final MeterRegistry meterRegistry;
    
    @KafkaListener(topics = "${app.notification.kafka.topics.user-registered}", groupId = "${spring.application.name}")
    public void onUserRegistered(UserRegisteredEvent event) {
        handleDraft(authNotificationFactory.fromUserRegistered(event));
        meterRegistry.counter("app.kafka.event.processed", "service", "notification-service", "event", "UserRegisteredEvent")
                .increment();
    }
    
    @KafkaListener(topics = "${app.notification.kafka.topics.user-email-changed}", groupId = "${spring.application.name}")
    public void onUserEmailChanged(UserEmailChangedEvent event) {
        handleDraft(authNotificationFactory.fromUserEmailChanged(event));
        meterRegistry.counter("app.kafka.event.processed", "service", "notification-service", "event", "UserEmailChangedEvent")
                .increment();
    }
    
    @KafkaListener(topics = "${app.notification.kafka.topics.user-username-changed}", groupId = "${spring.application.name}")
    public void onUserUsernameChanged(UserUsernameChangedEvent event) {
        handleDraft(authNotificationFactory.fromUserUsernameChanged(event));
        meterRegistry.counter("app.kafka.event.processed", "service", "notification-service", "event", "UserUsernameChangedEvent")
                .increment();
    }
    
    @KafkaListener(topics = "${app.notification.kafka.topics.user-banned}", groupId = "${spring.application.name}")
    public void onUserBanned(UserBannedEvent event) {
        handleDraft(authNotificationFactory.fromUserBanned(event));
        meterRegistry.counter("app.kafka.event.processed", "service", "notification-service", "event", "UserBannedEvent")
                .increment();
    }
    
    @KafkaListener(topics = "${app.notification.kafka.topics.user-unbanned}", groupId = "${spring.application.name}")
    public void onUserUnbanned(UserUnbannedEvent event) {
        handleDraft(authNotificationFactory.fromUserUnbanned(event));
        meterRegistry.counter("app.kafka.event.processed", "service", "notification-service", "event", "UserUnbannedEvent")
                .increment();
    }
    
    private void handleDraft(NotificationDraft draft) {
        log.info("Creating notification from {} for userId={}", draft.sourceEventType(), draft.userId());
        notificationService.createFromEvent(
                draft.userId(),
                draft.sourceEventId(),
                draft.sourceEventType(),
                draft.type(),
                draft.category(),
                draft.title(),
                draft.body(),
                draft.payload()
        );
    }
}
