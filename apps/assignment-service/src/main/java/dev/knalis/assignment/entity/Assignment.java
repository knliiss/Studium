package dev.knalis.assignment.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "assignments",
        indexes = {
                @Index(name = "idx_assignments_topic_id", columnList = "topicId"),
                @Index(name = "idx_assignments_status", columnList = "status"),
                @Index(name = "idx_assignments_created_by_user_id", columnList = "createdByUserId")
        }
)
public class Assignment {
    
    @Id
    private UUID id;
    
    @Column(nullable = false)
    private UUID topicId;

    @Column
    private UUID createdByUserId;
    
    @Column(nullable = false, length = 200)
    private String title;
    
    @Column(length = 2000)
    private String description;
    
    @Column(nullable = false)
    private Instant deadline;

    @Column(nullable = false)
    @ColumnDefault("0")
    private int orderIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssignmentStatus status = AssignmentStatus.DRAFT;

    @Column(nullable = false)
    private boolean allowLateSubmissions;

    @Column(nullable = false)
    private int maxSubmissions = 1;

    @Column(nullable = false)
    private boolean allowResubmit;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "assignment_accepted_file_types",
            joinColumns = @JoinColumn(name = "assignmentId")
    )
    @Column(name = "contentType", length = 100, nullable = false)
    private Set<String> acceptedFileTypes = new LinkedHashSet<>();

    @Column
    private Integer maxFileSizeMb;
    
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
