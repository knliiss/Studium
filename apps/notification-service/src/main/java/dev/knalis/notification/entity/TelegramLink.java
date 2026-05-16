package dev.knalis.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "telegram_links",
        indexes = {
                @Index(name = "idx_telegram_links_user_id_active", columnList = "userId,active"),
                @Index(name = "idx_telegram_links_telegram_user_id_active", columnList = "telegramUserId,active"),
                @Index(name = "idx_telegram_links_chat_id_active", columnList = "chatId,active")
        }
)
public class TelegramLink {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private Long telegramUserId;

    @Column(nullable = false)
    private Long chatId;

    @Column(length = 120)
    private String telegramUsername;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean telegramEnabled = true;

    @Column(nullable = false)
    private boolean notifyAssignments = true;

    @Column(nullable = false)
    private boolean notifyTests = true;

    @Column(nullable = false)
    private boolean notifyGrades = true;

    @Column(nullable = false)
    private boolean notifySchedule = true;

    @Column(nullable = false)
    private boolean notifyMaterials = true;

    @Column(nullable = false)
    private boolean notifySystem = true;

    @Column
    private Instant connectedAt;

    @Column
    private Instant disconnectedAt;

    @Column
    private Instant lastSeenAt;

    @Column(nullable = false)
    private long telegramSentCount = 0;

    @Column(nullable = false)
    private long deliveryFailureCount = 0;

    @Column(length = 300)
    private String lastDeliveryFailure;

    @Column
    private Instant lastDeliveryFailureAt;

    @Column
    private Instant lastDeliveredAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (connectedAt == null) {
            connectedAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
