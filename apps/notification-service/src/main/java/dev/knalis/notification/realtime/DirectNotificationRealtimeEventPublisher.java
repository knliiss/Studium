package dev.knalis.notification.realtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.notification.realtime", name = "redis-fanout-enabled", havingValue = "false")
public class DirectNotificationRealtimeEventPublisher implements NotificationRealtimeEventPublisher {
    
    private final NotificationWebSocketDeliveryService notificationWebSocketDeliveryService;
    
    public DirectNotificationRealtimeEventPublisher(NotificationWebSocketDeliveryService notificationWebSocketDeliveryService) {
        this.notificationWebSocketDeliveryService = notificationWebSocketDeliveryService;
    }
    
    @Override
    public void publish(NotificationSocketMessage message) {
        notificationWebSocketDeliveryService.deliver(message);
    }
}
