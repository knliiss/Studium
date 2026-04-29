package dev.knalis.file.controller;

import dev.knalis.file.dto.request.FileScanResultRequest;
import dev.knalis.file.dto.response.FileCleanupReportResponse;
import dev.knalis.file.dto.response.StoredFileResponse;
import dev.knalis.file.service.FileCleanupService;
import dev.knalis.file.service.InternalRequestGuard;
import dev.knalis.file.service.StoredFileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/files")
@RequiredArgsConstructor
public class InternalFileController {
    
    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";
    
    private final StoredFileService storedFileService;
    private final FileCleanupService fileCleanupService;
    private final InternalRequestGuard internalRequestGuard;
    
    @PostMapping("/{fileId}/scan-result")
    public StoredFileResponse reportScanResult(
            @PathVariable UUID fileId,
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret,
            @Valid @RequestBody FileScanResultRequest request
    ) {
        internalRequestGuard.verify(sharedSecret);
        return Boolean.TRUE.equals(request.clean())
                ? storedFileService.markScanReady(fileId, request.message())
                : storedFileService.markScanRejected(fileId, request.message());
    }
    
    @GetMapping("/cleanup/report")
    public FileCleanupReportResponse cleanupReport(
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret
    ) {
        internalRequestGuard.verify(sharedSecret);
        return fileCleanupService.buildReport();
    }
    
    @PostMapping("/cleanup/run")
    public ResponseEntity<Void> runCleanup(
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret
    ) {
        internalRequestGuard.verify(sharedSecret);
        fileCleanupService.cleanupNow();
        return ResponseEntity.accepted().build();
    }
}
