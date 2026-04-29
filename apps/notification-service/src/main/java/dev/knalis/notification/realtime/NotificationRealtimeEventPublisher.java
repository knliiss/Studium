package dev.knalis.notification.realtime;

public interface NotificationRealtimeEventPublisher {
    
    void publish(NotificationSocketMessage message);
}
