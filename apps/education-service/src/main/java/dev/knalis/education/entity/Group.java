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
        name = "groups",
        indexes = {
                @Index(name = "idx_groups_specialty_id", columnList = "specialty_id"),
                @Index(name = "idx_groups_study_year", columnList = "study_year"),
                @Index(name = "idx_groups_stream_id", columnList = "stream_id")
        }
)
public class Group {
    
    @Id
    private UUID id;
    
    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "specialty_id")
    private UUID specialtyId;

    @Column(name = "study_year")
    private Integer studyYear;

    @Column(name = "stream_id")
    private UUID streamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subgroup_mode", nullable = false, length = 50)
    private GroupSubgroupMode subgroupMode;
    
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
        if (subgroupMode == null) {
            subgroupMode = GroupSubgroupMode.NONE;
        }
        updatedAt = now;
    }
    
    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
