package dev.knalis.notification.config;

import dev.knalis.notification.websocket.WebSocketAuthChannelInterceptor;
import dev.knalis.notification.websocket.WebSocketTokenHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    private final NotificationRealtimeProperties notificationRealtimeProperties;
    private final WebSocketAuthChannelInterceptor webSocketAuthChannelInterceptor;
    private final WebSocketTokenHandshakeInterceptor webSocketTokenHandshakeInterceptor;
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.setPreservePublishOrder(true);
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(notificationRealtimeProperties.getWebsocketEndpoint())
                .setAllowedOriginPatterns(notificationRealtimeProperties.getAllowedOriginPatterns().toArray(new String[0]))
                .addInterceptors(webSocketTokenHandshakeInterceptor);
    }
    
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthChannelInterceptor);
    }
    
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setSendTimeLimit(notificationRealtimeProperties.getSendTimeLimitMs());
        registry.setSendBufferSizeLimit(notificationRealtimeProperties.getSendBufferSizeLimitBytes());
        registry.setMessageSizeLimit(notificationRealtimeProperties.getMessageSizeLimitBytes());
    }
}
