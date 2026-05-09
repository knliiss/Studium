package dev.knalis.education.entity;

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
        name = "lecture_attachments",
        indexes = {
                @Index(name = "idx_lecture_attachments_lecture_id", columnList = "lectureId"),
                @Index(name = "idx_lecture_attachments_file_id", columnList = "fileId"),
                @Index(name = "idx_lecture_attachments_uploaded_by_user_id", columnList = "uploadedByUserId")
        }
)
public class LectureAttachment {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID lectureId;

    @Column(nullable = false)
    private UUID fileId;

    @Column(length = 255)
    private String displayName;

    @Column(nullable = false, length = 500)
    private String originalFileName;

    @Column(length = 255)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column
    private UUID uploadedByUserId;

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
