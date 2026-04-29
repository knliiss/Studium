package dev.knalis.auth.service.common;

import dev.knalis.auth.config.KafkaTopicsProperties;
import dev.knalis.contracts.event.UserBannedEvent;
import dev.knalis.contracts.event.UserEmailChangedEvent;
import dev.knalis.contracts.event.UserRegisteredEvent;
import dev.knalis.contracts.event.UserUnbannedEvent;
import dev.knalis.contracts.event.UserUsernameChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthEventPublisher {
    
    private final AuthOutboxService authOutboxService;
    private final KafkaTopicsProperties topics;

    public void publishUserRegistered(UserRegisteredEvent event) {
        authOutboxService.enqueue(topics.getUserRegistered(), event.userId().toString(), event);
    }

    public void publishUserEmailChanged(UserEmailChangedEvent event) {
        authOutboxService.enqueue(topics.getUserEmailChanged(), event.userId().toString(), event);
    }
    
    public void publishUserUsernameChanged(UserUsernameChangedEvent event) {
        authOutboxService.enqueue(topics.getUserUsernameChanged(), event.userId().toString(), event);
    }
    
    public void publishUserBanned(UserBannedEvent event) {
        authOutboxService.enqueue(topics.getUserBanned(), event.userId().toString(), event);
    }
    
    public void publishUserUnbanned(UserUnbannedEvent event) {
        authOutboxService.enqueue(topics.getUserUnbanned(), event.userId().toString(), event);
    }
    
}
