package dev.knalis.file.entity;

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
        name = "stored_files",
        indexes = {
                @Index(name = "idx_stored_files_owner_id", columnList = "ownerId"),
                @Index(name = "idx_stored_files_last_accessed_at", columnList = "lastAccessedAt"),
                @Index(name = "idx_stored_files_deleted", columnList = "deleted"),
                @Index(name = "idx_stored_files_object_key", columnList = "objectKey", unique = true)
        }
)
public class StoredFile {
    
    @Id
    private UUID id;
    
    @Column(nullable = false)
    private UUID ownerId;
    
    @Column(nullable = false, length = 255)
    private String originalFileName;
    
    @Column(nullable = false, length = 255)
    private String objectKey;
    
    @Column(nullable = false, length = 100)
    private String bucketName;
    
    @Column(nullable = false, length = 100)
    private String contentType;
    
    @Column(nullable = false)
    private long sizeBytes;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StoredFileKind fileKind;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StoredFileAccess access;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StoredFileStatus status = StoredFileStatus.UPLOADED;
    
    @Column(nullable = false)
    private boolean deleted = false;
    
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(nullable = false)
    private Instant updatedAt;
    
    @Column(nullable = false)
    private Instant lastAccessedAt;
    
    @Column
    private Instant scanCompletedAt;
    
    @Column(length = 500)
    private String scanStatusMessage;
    
    @Column
    private Instant deletedAt;
    
    @Column
    private UUID deletedBy;
    
    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (lastAccessedAt == null) {
            lastAccessedAt = now;
        }
        updatedAt = now;
    }
    
    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
