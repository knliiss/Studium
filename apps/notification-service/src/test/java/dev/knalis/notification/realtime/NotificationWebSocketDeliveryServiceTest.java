package dev.knalis.notification.realtime;

import dev.knalis.notification.config.NotificationRealtimeProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationWebSocketDeliveryServiceTest {
    
    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;
    
    private NotificationSessionRegistry notificationSessionRegistry;
    private NotificationWebSocketDeliveryService notificationWebSocketDeliveryService;
    
    @BeforeEach
    void setUp() {
        NotificationRealtimeProperties properties = new NotificationRealtimeProperties();
        notificationSessionRegistry = new NotificationSessionRegistry();
        notificationWebSocketDeliveryService = new NotificationWebSocketDeliveryService(
                simpMessagingTemplate,
                notificationSessionRegistry,
                properties,
                new SimpleMeterRegistry()
        );
    }
    
    @Test
    void deliverSkipsWhenUserHasNoLocalSessions() {
        notificationWebSocketDeliveryService.deliver(message());
        
        verify(simpMessagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }
    
    @Test
    void deliverSendsWhenUserHasLocalSession() {
        NotificationSocketMessage message = message();
        notificationSessionRegistry.register(message.userId().toString(), "session-1");
        
        notificationWebSocketDeliveryService.deliver(message);
        
        verify(simpMessagingTemplate).convertAndSendToUser(
                message.userId().toString(),
                "/queue/notifications",
                message
        );
    }
    
    private NotificationSocketMessage message() {
        return new NotificationSocketMessage(
                NotificationSocketEventType.CREATED,
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                1L,
                Instant.now()
        );
    }
}
