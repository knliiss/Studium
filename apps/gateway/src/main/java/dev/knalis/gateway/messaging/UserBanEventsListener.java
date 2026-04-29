package dev.knalis.gateway.messaging;

import dev.knalis.contracts.event.UserBannedEvent;
import dev.knalis.contracts.event.UserUnbannedEvent;
import dev.knalis.gateway.service.BanStateService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserBanEventsListener {

    private final BanStateService banStateService;
    private final MeterRegistry meterRegistry;

    @KafkaListener(
            topics = "${app.gateway.kafka.topics.user-banned}",
            groupId = "${spring.application.name}"
    )
    public void onUserBanned(UserBannedEvent event) {
        log.info("Received UserBannedEvent: eventId={}, userId={}", event.eventId(), event.userId());
        banStateService.rememberBan(event);
        meterRegistry.counter("app.kafka.event.processed", "service", "gateway", "event", "UserBannedEvent")
                .increment();
    }

    @KafkaListener(
            topics = "${app.gateway.kafka.topics.user-unbanned}",
            groupId = "${spring.application.name}"
    )
    public void onUserUnbanned(UserUnbannedEvent event) {
        log.info("Received UserUnbannedEvent: eventId={}, userId={}", event.eventId(), event.userId());
        banStateService.rememberUnban(event);
        meterRegistry.counter("app.kafka.event.processed", "service", "gateway", "event", "UserUnbannedEvent")
                .increment();
    }
}
