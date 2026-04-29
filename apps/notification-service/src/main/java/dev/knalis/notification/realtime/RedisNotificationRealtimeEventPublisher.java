package dev.knalis.notification.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.notification.config.NotificationRealtimeProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.notification.realtime", name = "redis-fanout-enabled", havingValue = "true", matchIfMissing = true)
public class RedisNotificationRealtimeEventPublisher implements NotificationRealtimeEventPublisher {
    
    private final StringRedisTemplate stringRedisTemplate;
    private final NotificationRealtimeProperties notificationRealtimeProperties;
    private final ObjectMapper objectMapper;
    
    public RedisNotificationRealtimeEventPublisher(
            StringRedisTemplate stringRedisTemplate,
            NotificationRealtimeProperties notificationRealtimeProperties,
            ObjectMapper objectMapper
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.notificationRealtimeProperties = notificationRealtimeProperties;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void publish(NotificationSocketMessage message) {
        try {
            stringRedisTemplate.convertAndSend(
                    notificationRealtimeProperties.getRedisTopic(),
                    objectMapper.writeValueAsString(message)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize realtime notification", exception);
        }
    }
}
