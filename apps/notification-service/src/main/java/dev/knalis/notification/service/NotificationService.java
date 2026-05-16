package dev.knalis.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.notification.dto.request.CreateInternalNotificationRequest;
import dev.knalis.notification.dto.response.NotificationPageResponse;
import dev.knalis.notification.dto.response.NotificationResponse;
import dev.knalis.notification.dto.response.UnreadCountResponse;
import dev.knalis.notification.entity.Notification;
import dev.knalis.notification.entity.NotificationCategory;
import dev.knalis.notification.entity.NotificationStatus;
import dev.knalis.notification.entity.NotificationType;
import dev.knalis.notification.exception.NotificationNotFoundException;
import dev.knalis.notification.realtime.NotificationRealtimeEventPublisher;
import dev.knalis.notification.realtime.NotificationSocketEventType;
import dev.knalis.notification.realtime.NotificationSocketMessage;
import dev.knalis.notification.repository.NotificationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt",
            "updatedAt",
            "title"
    );
    
    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final NotificationRealtimeEventPublisher notificationRealtimeEventPublisher;
    private final TelegramNotificationDeliveryService telegramNotificationDeliveryService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    @Transactional
    public NotificationResponse createFromEvent(
            UUID userId,
            UUID sourceEventId,
            String sourceEventType,
            NotificationType type,
            NotificationCategory category,
            String title,
            String body,
            Map<String, Object> payload
    ) {
        Notification existing = sourceEventId == null
                ? null
                : notificationRepository.findBySourceEventId(sourceEventId).orElse(null);
        if (existing != null) {
            return notificationMapper.toResponse(existing);
        }
        
        return saveAndPublish(newNotification(
                userId,
                type,
                category,
                title,
                body,
                payload,
                sourceEventId,
                sourceEventType
        ));
    }
    
    @Transactional
    public NotificationResponse createInternal(UUID userId, CreateInternalNotificationRequest request) {
        return saveAndPublish(newNotification(
                userId,
                request.type(),
                request.category(),
                request.title(),
                request.body(),
                request.payload(),
                null,
                "INTERNAL"
        ));
    }
    
    @Transactional(readOnly = true)
    public NotificationPageResponse getMyNotifications(
            UUID userId,
            NotificationStatus status,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(resolveSortDirection(direction), resolveSortField(sortBy))
        );
        Page<Notification> notificationPage = status == null
                ? notificationRepository.findAllByUserId(userId, pageRequest)
                : notificationRepository.findAllByUserIdAndStatus(userId, status, pageRequest);
        
        return new NotificationPageResponse(
                notificationPage.getContent().stream().map(notificationMapper::toResponse).toList(),
                notificationPage.getNumber(),
                notificationPage.getSize(),
                notificationPage.getTotalElements(),
                notificationPage.getTotalPages(),
                notificationPage.isFirst(),
                notificationPage.isLast()
        );
    }
    
    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(UUID userId) {
        return new UnreadCountResponse(notificationRepository.countByUserIdAndStatus(userId, NotificationStatus.UNREAD));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadNotifications(UUID userId, int limit) {
        int boundedLimit = Math.min(Math.max(limit, 1), 20);
        PageRequest pageRequest = PageRequest.of(0, boundedLimit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Notification> notificationPage = notificationRepository.findAllByUserIdAndStatus(
                userId,
                NotificationStatus.UNREAD,
                pageRequest
        );
        return notificationPage.getContent().stream()
                .map(notificationMapper::toResponse)
                .toList();
    }
    
    @Transactional
    public NotificationResponse markAsRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        
        if (notification.getStatus() != NotificationStatus.UNREAD) {
            return notificationMapper.toResponse(notification);
        }
        
        notification.setStatus(NotificationStatus.READ);
        notification.setReadAt(Instant.now());
        Notification saved = notificationRepository.save(notification);
        long unreadCount = unreadCountValue(userId);
        publishRealtime(NotificationSocketEventType.READ, saved, unreadCount);
        meterRegistry.counter("app.notification.read", "service", "notification-service").increment();
        return notificationMapper.toResponse(saved);
    }
    
    @Transactional
    public UnreadCountResponse markAllAsRead(UUID userId) {
        int updated = notificationRepository.markAllAsRead(
                userId,
                NotificationStatus.UNREAD,
                NotificationStatus.READ,
                Instant.now()
        );
        long unreadCount = unreadCountValue(userId);
        if (updated > 0) {
            notificationRealtimeEventPublisher.publish(new NotificationSocketMessage(
                    NotificationSocketEventType.READ_ALL,
                    userId,
                    null,
                    null,
                    unreadCount,
                    Instant.now()
            ));
            meterRegistry.counter("app.notification.read_all", "service", "notification-service").increment();
        }
        return new UnreadCountResponse(unreadCount);
    }
    
    @Transactional
    public void delete(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        
        notificationRepository.delete(notification);
        long unreadCount = unreadCountValue(userId);
        notificationRealtimeEventPublisher.publish(new NotificationSocketMessage(
                NotificationSocketEventType.DELETED,
                userId,
                notificationId,
                null,
                unreadCount,
                Instant.now()
        ));
        meterRegistry.counter("app.notification.deleted", "service", "notification-service").increment();
    }

    @Transactional
    public UnreadCountResponse deleteAll(UUID userId) {
        int deleted = notificationRepository.deleteAllByUserId(userId);
        if (deleted > 0) {
            notificationRealtimeEventPublisher.publish(new NotificationSocketMessage(
                    NotificationSocketEventType.DELETED,
                    userId,
                    null,
                    null,
                    0,
                    Instant.now()
            ));
            meterRegistry.counter("app.notification.deleted_all", "service", "notification-service").increment();
        }
        return new UnreadCountResponse(0);
    }
    
    private Notification newNotification(
            UUID userId,
            NotificationType type,
            NotificationCategory category,
            String title,
            String body,
            Map<String, Object> payload,
            UUID sourceEventId,
            String sourceEventType
    ) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setCategory(category);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setPayloadJson(serializePayload(payload));
        notification.setSourceEventId(sourceEventId);
        notification.setSourceEventType(sourceEventType);
        notification.setStatus(NotificationStatus.UNREAD);
        return notification;
    }
    
    private NotificationResponse saveAndPublish(Notification notification) {
        try {
            Notification saved = notificationRepository.save(notification);
            long unreadCount = unreadCountValue(saved.getUserId());
            publishRealtime(NotificationSocketEventType.CREATED, saved, unreadCount);
            telegramNotificationDeliveryService.deliver(saved);
            meterRegistry.counter(
                    "app.notification.created",
                    "service", "notification-service",
                    "type", saved.getType().name()
            ).increment();
            return notificationMapper.toResponse(saved);
        } catch (DataIntegrityViolationException exception) {
            if (notification.getSourceEventId() == null) {
                throw exception;
            }
            log.info("Skipped duplicate notification for sourceEventId={}", notification.getSourceEventId());
            Notification existing = notificationRepository.findBySourceEventId(notification.getSourceEventId())
                    .orElseThrow(() -> exception);
            return notificationMapper.toResponse(existing);
        }
    }
    
    private void publishRealtime(NotificationSocketEventType eventType, Notification notification, long unreadCount) {
        notificationRealtimeEventPublisher.publish(new NotificationSocketMessage(
                eventType,
                notification.getUserId(),
                notification.getId(),
                notificationMapper.toResponse(notification),
                unreadCount,
                Instant.now()
        ));
    }
    
    private long unreadCountValue(UUID userId) {
        return notificationRepository.countByUserIdAndStatus(userId, NotificationStatus.UNREAD);
    }
    
    private String serializePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize notification payload", exception);
        }
    }

    private String resolveSortField(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "createdAt";
        }
        return ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "createdAt";
    }

    private Sort.Direction resolveSortDirection(String direction) {
        return "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
    }
}
