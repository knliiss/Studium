package dev.knalis.notification.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.notification.realtime", name = "redis-fanout-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationRedisRealtimeSubscriber implements MessageListener {
    
    private final ObjectMapper objectMapper;
    private final NotificationWebSocketDeliveryService notificationWebSocketDeliveryService;
    
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            NotificationSocketMessage socketMessage = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8),
                    NotificationSocketMessage.class
            );
            notificationWebSocketDeliveryService.deliver(socketMessage);
        } catch (Exception exception) {
            log.error("Failed to consume realtime notification from redis: {}", exception.getMessage(), exception);
        }
    }
}
