package dev.knalis.notification.realtime;

import dev.knalis.notification.config.NotificationRealtimeProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationWebSocketDeliveryService {
    
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final NotificationSessionRegistry notificationSessionRegistry;
    private final NotificationRealtimeProperties notificationRealtimeProperties;
    private final MeterRegistry meterRegistry;
    
    public void deliver(NotificationSocketMessage message) {
        String userId = message.userId().toString();
        if (!notificationSessionRegistry.hasSessions(userId)) {
            meterRegistry.counter("app.notification.realtime.skipped", "service", "notification-service").increment();
            return;
        }
        
        simpMessagingTemplate.convertAndSendToUser(
                userId,
                notificationRealtimeProperties.getUserDestination(),
                message
        );
        meterRegistry.counter("app.notification.realtime.delivered", "service", "notification-service").increment();
        log.debug("Delivered realtime notification eventType={} userId={}", message.eventType(), userId);
    }
}
