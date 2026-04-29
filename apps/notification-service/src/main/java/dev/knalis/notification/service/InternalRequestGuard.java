package dev.knalis.notification.service;

import dev.knalis.notification.config.NotificationInternalProperties;
import dev.knalis.notification.exception.InvalidInternalRequestException;
import org.springframework.stereotype.Service;

@Service
public class InternalRequestGuard {
    
    private final NotificationInternalProperties notificationInternalProperties;
    
    public InternalRequestGuard(NotificationInternalProperties notificationInternalProperties) {
        this.notificationInternalProperties = notificationInternalProperties;
    }
    
    public void verify(String sharedSecret) {
        if (sharedSecret == null || !sharedSecret.equals(notificationInternalProperties.getSharedSecret())) {
            throw new InvalidInternalRequestException();
        }
    }
}
