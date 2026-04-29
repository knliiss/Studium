package dev.knalis.notification.config;

import dev.knalis.notification.realtime.NotificationRedisRealtimeSubscriber;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class NotificationRedisConfig {
    
    @Bean
    @ConditionalOnProperty(prefix = "app.notification.realtime", name = "redis-fanout-enabled", havingValue = "true", matchIfMissing = true)
    public ChannelTopic notificationRealtimeChannelTopic(NotificationRealtimeProperties properties) {
        return new ChannelTopic(properties.getRedisTopic());
    }
    
    @Bean
    @ConditionalOnProperty(prefix = "app.notification.realtime", name = "redis-fanout-enabled", havingValue = "true", matchIfMissing = true)
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            NotificationRedisRealtimeSubscriber subscriber,
            ChannelTopic notificationRealtimeChannelTopic
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, notificationRealtimeChannelTopic);
        return container;
    }
}
