package dev.knalis.notification.repository;

import dev.knalis.notification.entity.Notification;
import dev.knalis.notification.entity.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    
    Optional<Notification> findBySourceEventId(UUID sourceEventId);
    
    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);
    
    Page<Notification> findAllByUserId(UUID userId, Pageable pageable);
    
    Page<Notification> findAllByUserIdAndStatus(
            UUID userId,
            NotificationStatus status,
            Pageable pageable
    );
    
    long countByUserIdAndStatus(UUID userId, NotificationStatus status);
    
    @Modifying
    @Query("""
            update Notification notification
            set notification.status = :readStatus,
                notification.readAt = :readAt
            where notification.userId = :userId
              and notification.status = :unreadStatus
            """)
    int markAllAsRead(
            @Param("userId") UUID userId,
            @Param("unreadStatus") NotificationStatus unreadStatus,
            @Param("readStatus") NotificationStatus readStatus,
            @Param("readAt") Instant readAt
    );
}
