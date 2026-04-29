package dev.knalis.education.entity;

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
        name = "topics",
        indexes = {
                @Index(name = "idx_topics_subject_id", columnList = "subjectId"),
                @Index(name = "uk_topics_subject_id_order_index", columnList = "subjectId, orderIndex", unique = true)
        }
)
public class Topic {
    
    @Id
    private UUID id;
    
    @Column(nullable = false)
    private UUID subjectId;
    
    @Column(nullable = false, length = 200)
    private String title;
    
    @Column(nullable = false)
    private int orderIndex;
    
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
