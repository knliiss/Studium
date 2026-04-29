package dev.knalis.notification.realtime;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationSessionRegistry {
    
    private final ConcurrentHashMap<String, Set<String>> sessionsByUserId = new ConcurrentHashMap<>();
    
    public void register(String userId, String sessionId) {
        sessionsByUserId.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }
    
    public void unregister(String userId, String sessionId) {
        sessionsByUserId.computeIfPresent(userId, (ignored, sessions) -> {
            sessions.remove(sessionId);
            return sessions.isEmpty() ? null : sessions;
        });
    }
    
    public boolean hasSessions(String userId) {
        Set<String> sessions = sessionsByUserId.get(userId);
        return sessions != null && !sessions.isEmpty();
    }
}
