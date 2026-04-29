package dev.knalis.profile.entity;

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
        name = "user_profiles",
        indexes = {
                @Index(name = "idx_user_profiles_user_id", columnList = "userId", unique = true)
        }
)
public class UserProfile {
    
    @Id
    private UUID id;
    
    @Column(nullable = false, unique = true)
    private UUID userId;
    
    @Column(nullable = false, length = 100)
    private String username;
    
    @Column(length = 255)
    private String email;
    
    @Column(nullable = false, length = 100)
    private String displayName;
    
    @Column(length = 255)
    private String avatarFileKey;
    
    @Column(length = 20)
    private String locale;
    
    @Column(length = 50)
    private String timezone;
    
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
        updatedAt = now;
    }
    
    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
