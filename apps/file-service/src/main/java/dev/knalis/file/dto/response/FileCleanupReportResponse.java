package dev.knalis.file.dto.response;

import java.time.Instant;

public record FileCleanupReportResponse(
        int staleUploadedCount,
        int staleOrphanedCount,
        int staleRejectedCount,
        int deletedMetadataCount,
        int batchSize,
        boolean cleanupEnabled,
        boolean dryRun,
        Instant lastRunAt,
        Integer lastRunCleanedUploaded,
        Integer lastRunCleanedOrphaned,
        Integer lastRunCleanedRejected,
        Integer lastRunPurgedMetadata
) {
}
