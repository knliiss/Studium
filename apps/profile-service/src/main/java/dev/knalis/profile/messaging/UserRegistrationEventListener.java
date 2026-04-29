package dev.knalis.profile.messaging;

import dev.knalis.contracts.event.UserRegisteredEvent;
import dev.knalis.profile.service.profile.UserProfileService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegistrationEventListener {

    private final UserProfileService userProfileService;
    private final MeterRegistry meterRegistry;

    @KafkaListener(
            topics = "${app.kafka.topics.user-registered}",
            groupId = "${spring.application.name}"
    )
    public void handleUserRegistered(UserRegisteredEvent event) {
        log.info("Received UserRegisteredEvent: eventId={}, userId={}", event.eventId(), event.userId());
        userProfileService.createProfileForRegisteredUser(event);
        meterRegistry.counter(
                "app.kafka.event.processed",
                "service", "profile-service",
                "event", "UserRegisteredEvent"
        ).increment();
    }
}
