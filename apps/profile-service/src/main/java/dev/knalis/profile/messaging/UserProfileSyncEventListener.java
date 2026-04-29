package dev.knalis.profile.messaging;

import dev.knalis.contracts.event.UserEmailChangedEvent;
import dev.knalis.contracts.event.UserUsernameChangedEvent;
import dev.knalis.profile.service.profile.UserProfileService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserProfileSyncEventListener {
    
    private final UserProfileService userProfileService;
    private final MeterRegistry meterRegistry;
    
    @KafkaListener(
            topics = "${app.kafka.topics.user-email-changed}",
            groupId = "${spring.application.name}"
    )
    public void handleUserEmailChanged(UserEmailChangedEvent event) {
        log.info("Received UserEmailChangedEvent: eventId={}, userId={}", event.eventId(), event.userId());
        userProfileService.syncEmail(event);
        meterRegistry.counter(
                "app.kafka.event.processed",
                "service", "profile-service",
                "event", "UserEmailChangedEvent"
        ).increment();
    }
    
    @KafkaListener(
            topics = "${app.kafka.topics.user-username-changed}",
            groupId = "${spring.application.name}"
    )
    public void handleUserUsernameChanged(UserUsernameChangedEvent event) {
        log.info("Received UserUsernameChangedEvent: eventId={}, userId={}", event.eventId(), event.userId());
        userProfileService.syncUsername(event);
        meterRegistry.counter(
                "app.kafka.event.processed",
                "service", "profile-service",
                "event", "UserUsernameChangedEvent"
        ).increment();
    }
}
