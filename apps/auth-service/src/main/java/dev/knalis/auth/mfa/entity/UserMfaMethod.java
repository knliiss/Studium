package dev.knalis.auth.mfa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "user_mfa_methods",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_mfa_methods_user_method", columnNames = {"user_id", "method_type"})
        },
        indexes = {
                @Index(name = "idx_user_mfa_methods_user_enabled", columnList = "user_id,enabled")
        }
)
public class UserMfaMethod {
    
    @Id
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "method_type", nullable = false, length = 30)
    private MfaMethodType methodType;
    
    @Column(name = "enabled", nullable = false)
    private boolean enabled;
    
    @Column(name = "preferred", nullable = false)
    private boolean preferred;
    
    @Column(name = "secret_encrypted", length = 2048)
    private String secretEncrypted;
    
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;
    
    @Column(name = "enabled_at")
    private Instant enabledAt;
    
    @Column(name = "disabled_at")
    private Instant disabledAt;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
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
        updatedAt = now;
    }
    
    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
