package dev.knalis.notification.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketSessionListener {
    
    private final NotificationSessionRegistry notificationSessionRegistry;
    
    @EventListener
    public void onSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        if (accessor.getUser() == null || accessor.getSessionId() == null) {
            return;
        }
        
        notificationSessionRegistry.register(accessor.getUser().getName(), accessor.getSessionId());
        log.info("WebSocket session connected userId={} sessionId={}", accessor.getUser().getName(), accessor.getSessionId());
    }
    
    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        if (event.getUser() == null || event.getSessionId() == null) {
            return;
        }
        
        notificationSessionRegistry.unregister(event.getUser().getName(), event.getSessionId());
        log.info("WebSocket session disconnected userId={} sessionId={}", event.getUser().getName(), event.getSessionId());
    }
}
