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
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "mfa_challenges",
        indexes = {
                @Index(name = "idx_mfa_challenges_token_hash", columnList = "token_hash", unique = true),
                @Index(name = "idx_mfa_challenges_user_status", columnList = "user_id,status"),
                @Index(name = "idx_mfa_challenges_expires_at", columnList = "expires_at")
        }
)
public class MfaChallenge {
    
    @Id
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;
    
    @Column(name = "available_methods_csv", nullable = false, length = 200)
    private String availableMethodsCsv;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "selected_method", length = 30)
    private MfaMethodType selectedMethod;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MfaChallengeStatus status = MfaChallengeStatus.PENDING_SELECTION;
    
    @Column(name = "verification_code_hash", length = 128)
    private String verificationCodeHash;
    
    @Column(name = "verification_code_expires_at")
    private Instant verificationCodeExpiresAt;
    
    @Column(name = "dispatch_count", nullable = false)
    private int dispatchCount;
    
    @Column(name = "verification_attempts", nullable = false)
    private int verificationAttempts;
    
    @Column(name = "challenge_number")
    private Integer challengeNumber;
    
    @Column(name = "ip_address", length = 128)
    private String ipAddress;
    
    @Column(name = "user_agent", length = 1000)
    private String userAgent;
    
    @Column(name = "selected_at")
    private Instant selectedAt;
    
    @Column(name = "completed_at")
    private Instant completedAt;
    
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    
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
