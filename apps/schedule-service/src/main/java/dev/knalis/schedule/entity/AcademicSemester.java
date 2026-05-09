package dev.knalis.schedule.entity;

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
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "academic_semesters",
        indexes = {
                @Index(name = "idx_academic_semesters_active", columnList = "active"),
                @Index(name = "idx_academic_semesters_published", columnList = "published"),
                @Index(name = "idx_academic_semesters_start_date", columnList = "startDate"),
                @Index(name = "idx_academic_semesters_end_date", columnList = "endDate")
        }
)
public class AcademicSemester {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private LocalDate weekOneStartDate;

    @Column(nullable = false)
    private Integer semesterNumber;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean published;

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
