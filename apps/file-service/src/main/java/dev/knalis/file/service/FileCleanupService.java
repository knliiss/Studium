package dev.knalis.file.service;

import dev.knalis.file.config.FileCleanupProperties;
import dev.knalis.file.dto.response.FileCleanupReportResponse;
import dev.knalis.file.entity.StoredFile;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileCleanupService {
    
    private static final long CLEANUP_LOCK_KEY = 4_206_001L;
    
    private final FileCleanupProperties fileCleanupProperties;
    private final StoredFileService storedFileService;
    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;
    
    private final AtomicReference<CleanupSummary> lastCleanupSummary = new AtomicReference<>();
    
    @Scheduled(fixedDelayString = "${app.file.cleanup.schedule-delay:30m}")
    public void cleanup() {
        if (!fileCleanupProperties.isEnabled()) {
            return;
        }
        cleanupNow();
    }
    
    public CleanupSummary cleanupNow() {
        Boolean lockAcquired = jdbcTemplate.queryForObject(
                "select pg_try_advisory_lock(?)",
                Boolean.class,
                CLEANUP_LOCK_KEY
        );
        if (!Boolean.TRUE.equals(lockAcquired)) {
            return CleanupSummary.lockNotAcquired();
        }
        
        try {
            CleanupSummary summary = performCleanup();
            lastCleanupSummary.set(summary);
            meterRegistry.counter("app.file.cleanup.run", "result", "completed").increment();
            return summary;
        } catch (RuntimeException exception) {
            meterRegistry.counter("app.file.cleanup.run", "result", "failed").increment();
            throw exception;
        } finally {
            jdbcTemplate.queryForObject("select pg_advisory_unlock(?)", Boolean.class, CLEANUP_LOCK_KEY);
        }
    }
    
    public FileCleanupReportResponse buildReport() {
        Instant now = Instant.now();
        CleanupSummary lastSummary = lastCleanupSummary.get();
        return new FileCleanupReportResponse(
                storedFileService.countStalePendingScanFiles(now.minus(fileCleanupProperties.getUploadedRetention()))
                        + storedFileService.countStaleUploadedFiles(now.minus(fileCleanupProperties.getUploadedRetention())),
                storedFileService.countStaleOrphanedFiles(now.minus(fileCleanupProperties.getOrphanedRetention())),
                storedFileService.countStaleRejectedFiles(now.minus(fileCleanupProperties.getRejectedRetention())),
                storedFileService.countDeletedMetadataForPurge(now.minus(fileCleanupProperties.getDeletedMetadataRetention())),
                fileCleanupProperties.getBatchSize(),
                fileCleanupProperties.isEnabled(),
                fileCleanupProperties.isDryRun(),
                lastSummary != null ? lastSummary.finishedAt() : null,
                lastSummary != null ? lastSummary.cleanedUploaded() : null,
                lastSummary != null ? lastSummary.cleanedOrphaned() : null,
                lastSummary != null ? lastSummary.cleanedRejected() : null,
                lastSummary != null ? lastSummary.purgedMetadata() : null
        );
    }
    
    private CleanupSummary performCleanup() {
        int cleanedUploaded = cleanupStaleUploads();
        int cleanedOrphaned = cleanupStaleOrphans();
        int cleanedRejected = cleanupRejectedFiles();
        int purgedMetadata = purgeDeletedMetadata();
        return new CleanupSummary(Instant.now(), cleanedUploaded, cleanedOrphaned, cleanedRejected, purgedMetadata, true);
    }
    
    private int cleanupStaleUploads() {
        Instant cutoff = Instant.now().minus(fileCleanupProperties.getUploadedRetention());
        List<StoredFile> uploadedFiles = storedFileService.findStaleUploadedFiles(cutoff)
                .stream()
                .limit(fileCleanupProperties.getBatchSize())
                .toList();
        List<StoredFile> pendingScanFiles = storedFileService.findStalePendingScanFiles(cutoff)
                .stream()
                .limit(Math.max(0, fileCleanupProperties.getBatchSize() - uploadedFiles.size()))
                .toList();
        return cleanupFiles(uploadedFiles, "uploaded") + cleanupFiles(pendingScanFiles, "pending_scan");
    }
    
    private int cleanupStaleOrphans() {
        Instant cutoff = Instant.now().minus(fileCleanupProperties.getOrphanedRetention());
        List<StoredFile> files = storedFileService.findStaleOrphanedFiles(cutoff)
                .stream()
                .limit(fileCleanupProperties.getBatchSize())
                .toList();
        return cleanupFiles(files, "orphaned");
    }
    
    private int cleanupRejectedFiles() {
        Instant cutoff = Instant.now().minus(fileCleanupProperties.getRejectedRetention());
        List<StoredFile> files = storedFileService.findRejectedFiles(cutoff)
                .stream()
                .limit(fileCleanupProperties.getBatchSize())
                .toList();
        return cleanupFiles(files, "rejected");
    }
    
    private int purgeDeletedMetadata() {
        Instant cutoff = Instant.now().minus(fileCleanupProperties.getDeletedMetadataRetention());
        List<StoredFile> files = storedFileService.findDeletedMetadataForPurge(cutoff)
                .stream()
                .limit(fileCleanupProperties.getBatchSize())
                .toList();
        for (StoredFile file : files) {
            if (fileCleanupProperties.isDryRun()) {
                log.info("Dry-run purge of deleted file metadata fileId={}", file.getId());
            } else {
                storedFileService.hardDeleteMetadata(file.getId());
                log.info("Purged deleted file metadata fileId={}", file.getId());
            }
        }
        return files.size();
    }
    
    private int cleanupFiles(List<StoredFile> files, String category) {
        for (StoredFile file : files) {
            if (fileCleanupProperties.isDryRun()) {
                log.info("Dry-run cleanup of stale {} file fileId={}", category, file.getId());
            } else {
                storedFileService.cleanupStaleFile(file.getId());
                log.info("Cleaned stale {} file fileId={}", category, file.getId());
            }
        }
        return files.size();
    }
    
    public record CleanupSummary(
            Instant finishedAt,
            int cleanedUploaded,
            int cleanedOrphaned,
            int cleanedRejected,
            int purgedMetadata,
            boolean lockAcquired
    ) {
        static CleanupSummary lockNotAcquired() {
            return new CleanupSummary(Instant.now(), 0, 0, 0, 0, false);
        }
    }
}
