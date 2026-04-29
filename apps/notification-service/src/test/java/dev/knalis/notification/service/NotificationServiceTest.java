package dev.knalis.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.notification.dto.request.CreateInternalNotificationRequest;
import dev.knalis.notification.entity.Notification;
import dev.knalis.notification.entity.NotificationCategory;
import dev.knalis.notification.entity.NotificationStatus;
import dev.knalis.notification.entity.NotificationType;
import dev.knalis.notification.realtime.NotificationRealtimeEventPublisher;
import dev.knalis.notification.repository.NotificationRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    
    @Mock
    private NotificationRepository notificationRepository;
    
    @Mock
    private NotificationRealtimeEventPublisher notificationRealtimeEventPublisher;
    
    private NotificationService notificationService;
    
    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                new NotificationMapper(),
                notificationRealtimeEventPublisher,
                new ObjectMapper(),
                new SimpleMeterRegistry()
        );
    }
    
    @Test
    void createFromEventSkipsDuplicateSourceEvent() {
        UUID userId = UUID.randomUUID();
        UUID sourceEventId = UUID.randomUUID();
        Notification existing = notification(userId, sourceEventId, NotificationStatus.UNREAD);
        
        when(notificationRepository.findBySourceEventId(sourceEventId)).thenReturn(Optional.of(existing));
        
        notificationService.createFromEvent(
                userId,
                sourceEventId,
                "UserRegisteredEvent",
                NotificationType.WELCOME,
                NotificationCategory.ACCOUNT,
                "Welcome",
                "Created",
                Map.of("username", "user")
        );
        
        verify(notificationRepository, never()).save(any(Notification.class));
        verify(notificationRealtimeEventPublisher, never()).publish(any());
    }
    
    @Test
    void createInternalSavesUnreadNotificationAndPublishesRealtimeEvent() {
        UUID userId = UUID.randomUUID();
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(UUID.randomUUID());
            notification.setCreatedAt(Instant.now());
            notification.setUpdatedAt(Instant.now());
            return notification;
        });
        when(notificationRepository.countByUserIdAndStatus(userId, NotificationStatus.UNREAD)).thenReturn(1L);
        
        notificationService.createInternal(userId, new CreateInternalNotificationRequest(
                NotificationType.GENERIC,
                NotificationCategory.SYSTEM,
                "Hello",
                "World",
                Map.of("foo", "bar")
        ));
        
        verify(notificationRepository).save(any(Notification.class));
        verify(notificationRealtimeEventPublisher).publish(any());
    }
    
    @Test
    void markAsReadPublishesRealtimeStateChange() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        Notification notification = notification(userId, null, NotificationStatus.UNREAD);
        notification.setId(notificationId);
        
        when(notificationRepository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);
        when(notificationRepository.countByUserIdAndStatus(userId, NotificationStatus.UNREAD)).thenReturn(0L);
        
        notificationService.markAsRead(userId, notificationId);
        
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertEquals(NotificationStatus.READ, notificationCaptor.getValue().getStatus());
        verify(notificationRealtimeEventPublisher).publish(any());
    }
    
    private Notification notification(UUID userId, UUID sourceEventId, NotificationStatus status) {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setUserId(userId);
        notification.setType(NotificationType.GENERIC);
        notification.setCategory(NotificationCategory.SYSTEM);
        notification.setTitle("Title");
        notification.setBody("Body");
        notification.setSourceEventId(sourceEventId);
        notification.setSourceEventType(sourceEventId == null ? null : "Event");
        notification.setStatus(status);
        notification.setCreatedAt(Instant.now());
        notification.setUpdatedAt(Instant.now());
        return notification;
    }
}
