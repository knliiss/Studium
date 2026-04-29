package dev.knalis.file.repository;

import dev.knalis.file.entity.StoredFile;
import dev.knalis.file.entity.StoredFileKind;
import dev.knalis.file.entity.StoredFileStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {
    
    Optional<StoredFile> findByIdAndDeletedFalse(UUID id);
    
    List<StoredFile> findAllByOwnerIdAndDeletedFalseOrderByCreatedAtDesc(UUID ownerId);
    
    List<StoredFile> findAllByOwnerIdAndFileKindAndDeletedFalseOrderByCreatedAtDesc(UUID ownerId, StoredFileKind fileKind);
    
    List<StoredFile> findTop100ByDeletedFalseAndLastAccessedAtBeforeOrderByLastAccessedAtAsc(Instant cutoff);
    
    long countByOwnerIdAndDeletedFalseAndStatusNot(UUID ownerId, StoredFileStatus status);
    
    long countByOwnerIdAndFileKindAndDeletedFalseAndStatusNot(UUID ownerId, StoredFileKind fileKind, StoredFileStatus status);
    
    @Query("""
            select coalesce(sum(sf.sizeBytes), 0)
            from StoredFile sf
            where sf.ownerId = :ownerId
              and sf.deleted = false
              and sf.status <> :excludedStatus
            """)
    long sumSizeBytesByOwnerIdExcludingStatus(
            @Param("ownerId") UUID ownerId,
            @Param("excludedStatus") StoredFileStatus excludedStatus
    );
    
    List<StoredFile> findTop100ByDeletedFalseAndStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            StoredFileStatus status,
            Instant cutoff
    );
    
    List<StoredFile> findTop100ByDeletedTrueAndDeletedAtBeforeOrderByDeletedAtAsc(Instant cutoff);
    
    int countByDeletedFalseAndStatusAndUpdatedAtBefore(StoredFileStatus status, Instant cutoff);
    
    int countByDeletedTrueAndDeletedAtBefore(Instant cutoff);
}
