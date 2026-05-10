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
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "topic_materials",
        indexes = {
                @Index(name = "idx_topic_materials_topic_id", columnList = "topicId"),
                @Index(name = "idx_topic_materials_visible", columnList = "visible"),
                @Index(name = "idx_topic_materials_archived", columnList = "archived"),
                @Index(name = "idx_topic_materials_order_index", columnList = "orderIndex"),
                @Index(name = "idx_topic_materials_file_id", columnList = "fileId")
        }
)
public class TopicMaterial {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID topicId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 5000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TopicMaterialType type;

    @Column(length = 2000)
    private String url;

    @Column
    private UUID fileId;

    @Column(length = 500)
    private String originalFileName;

    @Column(length = 255)
    private String contentType;

    @Column
    private Long sizeBytes;

    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean visible = true;

    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean archived = false;

    @Column(nullable = false)
    @ColumnDefault("0")
    private int orderIndex;

    @Column
    private UUID createdByUserId;

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

