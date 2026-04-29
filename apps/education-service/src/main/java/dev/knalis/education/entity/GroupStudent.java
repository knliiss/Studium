package dev.knalis.education.entity;

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
        name = "group_students",
        indexes = {
                @Index(name = "idx_group_students_group_id", columnList = "groupId"),
                @Index(name = "idx_group_students_user_id", columnList = "userId"),
                @Index(name = "uk_group_students_group_id_user_id", columnList = "groupId, userId", unique = true)
        }
)
public class GroupStudent {
    
    @Id
    private UUID id;
    
    @Column(nullable = false)
    private UUID groupId;
    
    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupMemberRole role = GroupMemberRole.STUDENT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Subgroup subgroup = Subgroup.ALL;
    
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
        if (role == null) {
            role = GroupMemberRole.STUDENT;
        }
        if (subgroup == null) {
            subgroup = Subgroup.ALL;
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
