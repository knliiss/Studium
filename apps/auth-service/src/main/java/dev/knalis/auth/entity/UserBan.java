package dev.knalis.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "user_bans",
        indexes = {
                @Index(name = "idx_user_bans_user_id", columnList = "userId"),
                @Index(name = "idx_user_bans_active", columnList = "active")
        }
)
public class UserBan {
    
    @Id
    private UUID id;
    
    @Column(nullable = false)
    private UUID userId;
    
    @Column(nullable = false, length = 500)
    private String reason;
    
    @Column
    private Instant expiresAt;
    
    @Column(nullable = false)
    private boolean active = true;
    
    @Column(nullable = false)
    private UUID createdBy;
    
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}