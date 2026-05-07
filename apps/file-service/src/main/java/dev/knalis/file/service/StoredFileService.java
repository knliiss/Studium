package dev.knalis.file.service;

import dev.knalis.file.config.FileScanProperties;
import dev.knalis.file.dto.response.StoredFileResponse;
import dev.knalis.file.entity.StoredFile;
import dev.knalis.file.entity.StoredFileAccess;
import dev.knalis.file.entity.StoredFileKind;
import dev.knalis.file.entity.StoredFileStatus;
import dev.knalis.file.exception.FileNotFoundException;
import dev.knalis.file.exception.FilePendingScanException;
import dev.knalis.file.exception.FilePreviewNotAvailableException;
import dev.knalis.file.exception.FileRejectedException;
import dev.knalis.file.exception.InvalidFileLifecycleTransitionException;
import dev.knalis.file.mapper.StoredFileMapper;
import dev.knalis.file.repository.StoredFileRepository;
import dev.knalis.file.service.storage.FileStorageObject;
import dev.knalis.file.service.storage.FileStorageService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StoredFileService {
    
    private final StoredFileRepository storedFileRepository;
    private final StoredFileMapper storedFileMapper;
    private final FilePolicyService filePolicyService;
    private final FileQuotaService fileQuotaService;
    private final FileStorageService fileStorageService;
    private final FileNameSanitizer fileNameSanitizer;
    private final FileObjectKeyFactory fileObjectKeyFactory;
    private final FileScanProperties fileScanProperties;
    private final MeterRegistry meterRegistry;
    
    @Transactional
    public StoredFileResponse upload(UUID ownerId, MultipartFile multipartFile, StoredFileKind fileKind) {
        filePolicyService.validateUpload(multipartFile, fileKind);
        fileQuotaService.validateQuota(ownerId, fileKind, multipartFile.getSize());
        
        UUID fileId = UUID.randomUUID();
        String originalFileName = fileNameSanitizer.sanitize(multipartFile.getOriginalFilename());
        String contentType = filePolicyService.contentType(multipartFile);
        StoredFileAccess access = filePolicyService.defaultAccess(fileKind);
        String bucketName = filePolicyService.bucketName(access);
        String objectKey = fileObjectKeyFactory.newObjectKey(ownerId, fileId, fileKind, originalFileName);
        
        fileStorageService.upload(bucketName, objectKey, multipartFile, contentType);
        
        try {
            StoredFile storedFile = new StoredFile();
            storedFile.setId(fileId);
            storedFile.setOwnerId(ownerId);
            storedFile.setOriginalFileName(originalFileName);
            storedFile.setObjectKey(objectKey);
            storedFile.setBucketName(bucketName);
            storedFile.setContentType(contentType);
            storedFile.setSizeBytes(multipartFile.getSize());
            storedFile.setFileKind(fileKind);
            storedFile.setAccess(access);
            storedFile.setStatus(initialStatus(fileKind));
            StoredFile saved = storedFileRepository.save(storedFile);
            meterRegistry.counter("app.file.upload.completed", "kind", fileKind.name()).increment();
            return storedFileMapper.toResponse(saved);
        } catch (RuntimeException exception) {
            fileStorageService.delete(bucketName, objectKey);
            meterRegistry.counter("app.file.upload.failed", "kind", fileKind.name()).increment();
            throw exception;
        }
    }
    
    @Transactional(readOnly = true)
    public StoredFileResponse getMetadata(UUID requesterId, UUID fileId) {
        return storedFileMapper.toResponse(requireAccessibleFile(requesterId, fileId));
    }
    
    @Transactional
    public FileDownload download(UUID requesterId, UUID fileId) {
        StoredFile storedFile = requireAccessibleFile(requesterId, fileId);
        assertDownloadAllowed(storedFile);
        storedFile.setLastAccessedAt(Instant.now());
        StoredFile saved = storedFileRepository.save(storedFile);
        FileStorageObject storageObject = fileStorageService.download(saved.getBucketName(), saved.getObjectKey());
        meterRegistry.counter("app.file.download.completed", "kind", saved.getFileKind().name()).increment();
        return new FileDownload(saved, storageObject);
    }
    
    @Transactional
    public FileDownload preview(UUID requesterId, UUID fileId) {
        StoredFile storedFile = requireAccessibleFile(requesterId, fileId);
        assertDownloadAllowed(storedFile);
        if (!isPreviewAvailable(storedFile.getContentType())) {
            throw new FilePreviewNotAvailableException(fileId);
        }
        
        storedFile.setLastAccessedAt(Instant.now());
        StoredFile saved = storedFileRepository.save(storedFile);
        FileStorageObject storageObject = fileStorageService.download(saved.getBucketName(), saved.getObjectKey());
        meterRegistry.counter("app.file.preview.completed", "kind", saved.getFileKind().name()).increment();
        return new FileDownload(saved, storageObject);
    }
    
    @Transactional
    public void delete(UUID requesterId, UUID fileId) {
        StoredFile storedFile = requireOwnedFile(requesterId, fileId);
        storedFile.setDeleted(true);
        storedFile.setStatus(StoredFileStatus.DELETED);
        storedFile.setDeletedAt(Instant.now());
        storedFile.setDeletedBy(requesterId);
        storedFileRepository.save(storedFile);
        fileStorageService.delete(storedFile.getBucketName(), storedFile.getObjectKey());
        meterRegistry.counter("app.file.deleted", "kind", storedFile.getFileKind().name()).increment();
    }
    
    @Transactional
    public StoredFileResponse markActive(UUID requesterId, UUID fileId) {
        StoredFile storedFile = requireOwnedFile(requesterId, fileId);
        assertReadyForActivation(storedFile);
        if (storedFile.getFileKind() == StoredFileKind.AVATAR) {
            demoteOtherActiveAvatars(requesterId, fileId);
        }
        storedFile.setStatus(StoredFileStatus.ACTIVE);
        StoredFile saved = storedFileRepository.save(storedFile);
        meterRegistry.counter("app.file.lifecycle.transition", "target", "ACTIVE").increment();
        return storedFileMapper.toResponse(saved);
    }
    
    @Transactional
    public StoredFileResponse markOrphaned(UUID requesterId, UUID fileId) {
        StoredFile storedFile = requireOwnedFile(requesterId, fileId);
        if (!storedFile.isDeleted()) {
            storedFile.setStatus(StoredFileStatus.ORPHANED);
        }
        StoredFile saved = storedFileRepository.save(storedFile);
        meterRegistry.counter("app.file.lifecycle.transition", "target", "ORPHANED").increment();
        return storedFileMapper.toResponse(saved);
    }
    
    @Transactional
    public StoredFileResponse markScanReady(UUID fileId, String message) {
        StoredFile storedFile = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new FileNotFoundException(fileId));
        if (storedFile.isDeleted()) {
            throw new InvalidFileLifecycleTransitionException(fileId, storedFile.getStatus(), "READY");
        }
        if (storedFile.getStatus() != StoredFileStatus.PENDING_SCAN
                && storedFile.getStatus() != StoredFileStatus.UPLOADED) {
            throw new InvalidFileLifecycleTransitionException(fileId, storedFile.getStatus(), "READY");
        }
        storedFile.setStatus(postScanStatus(storedFile));
        storedFile.setScanCompletedAt(Instant.now());
        storedFile.setScanStatusMessage(message);
        StoredFile saved = storedFileRepository.save(storedFile);
        meterRegistry.counter("app.file.scan.completed", "result", "clean").increment();
        return storedFileMapper.toResponse(saved);
    }
    
    @Transactional
    public StoredFileResponse markScanRejected(UUID fileId, String message) {
        StoredFile storedFile = storedFileRepository.findById(fileId)
                .orElseThrow(() -> new FileNotFoundException(fileId));
        if (storedFile.isDeleted()) {
            throw new InvalidFileLifecycleTransitionException(fileId, storedFile.getStatus(), "REJECTED");
        }
        if (storedFile.getStatus() == StoredFileStatus.ACTIVE || storedFile.getStatus() == StoredFileStatus.ORPHANED) {
            throw new InvalidFileLifecycleTransitionException(fileId, storedFile.getStatus(), "REJECTED");
        }
        storedFile.setStatus(StoredFileStatus.REJECTED);
        storedFile.setScanCompletedAt(Instant.now());
        storedFile.setScanStatusMessage(message);
        fileStorageService.delete(storedFile.getBucketName(), storedFile.getObjectKey());
        StoredFile saved = storedFileRepository.save(storedFile);
        meterRegistry.counter("app.file.scan.completed", "result", "rejected").increment();
        return storedFileMapper.toResponse(saved);
    }
    
    @Transactional(readOnly = true)
    public List<StoredFileResponse> listMine(UUID ownerId, StoredFileKind fileKind) {
        List<StoredFile> files = fileKind == null
                ? storedFileRepository.findAllByOwnerIdAndDeletedFalseOrderByCreatedAtDesc(ownerId)
                : storedFileRepository.findAllByOwnerIdAndFileKindAndDeletedFalseOrderByCreatedAtDesc(ownerId, fileKind);
        return files.stream().map(storedFileMapper::toResponse).toList();
    }
    
    @Transactional(readOnly = true)
    public List<StoredFileResponse> findInactiveFilesBefore(Instant cutoff) {
        return storedFileRepository.findTop100ByDeletedFalseAndLastAccessedAtBeforeOrderByLastAccessedAtAsc(cutoff)
                .stream()
                .map(storedFileMapper::toResponse)
                .toList();
    }
    
    @Transactional(readOnly = true)
    public List<StoredFile> findStaleUploadedFiles(Instant cutoff) {
        return storedFileRepository.findTop100ByDeletedFalseAndStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                StoredFileStatus.UPLOADED,
                cutoff
        );
    }
    
    @Transactional(readOnly = true)
    public List<StoredFile> findStalePendingScanFiles(Instant cutoff) {
        return storedFileRepository.findTop100ByDeletedFalseAndStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                StoredFileStatus.PENDING_SCAN,
                cutoff
        );
    }
    
    @Transactional(readOnly = true)
    public List<StoredFile> findStaleOrphanedFiles(Instant cutoff) {
        return storedFileRepository.findTop100ByDeletedFalseAndStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                StoredFileStatus.ORPHANED,
                cutoff
        );
    }
    
    @Transactional(readOnly = true)
    public List<StoredFile> findDeletedMetadataForPurge(Instant cutoff) {
        return storedFileRepository.findTop100ByDeletedTrueAndDeletedAtBeforeOrderByDeletedAtAsc(cutoff);
    }
    
    @Transactional(readOnly = true)
    public List<StoredFile> findRejectedFiles(Instant cutoff) {
        return storedFileRepository.findTop100ByDeletedFalseAndStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                StoredFileStatus.REJECTED,
                cutoff
        );
    }
    
    @Transactional(readOnly = true)
    public int countStaleUploadedFiles(Instant cutoff) {
        return storedFileRepository.countByDeletedFalseAndStatusAndUpdatedAtBefore(StoredFileStatus.UPLOADED, cutoff);
    }
    
    @Transactional(readOnly = true)
    public int countStalePendingScanFiles(Instant cutoff) {
        return storedFileRepository.countByDeletedFalseAndStatusAndUpdatedAtBefore(StoredFileStatus.PENDING_SCAN, cutoff);
    }
    
    @Transactional(readOnly = true)
    public int countStaleOrphanedFiles(Instant cutoff) {
        return storedFileRepository.countByDeletedFalseAndStatusAndUpdatedAtBefore(StoredFileStatus.ORPHANED, cutoff);
    }
    
    @Transactional(readOnly = true)
    public int countStaleRejectedFiles(Instant cutoff) {
        return storedFileRepository.countByDeletedFalseAndStatusAndUpdatedAtBefore(StoredFileStatus.REJECTED, cutoff);
    }
    
    @Transactional(readOnly = true)
    public int countDeletedMetadataForPurge(Instant cutoff) {
        return storedFileRepository.countByDeletedTrueAndDeletedAtBefore(cutoff);
    }
    
    @Transactional
    public void hardDeleteMetadata(UUID fileId) {
        storedFileRepository.deleteById(fileId);
        meterRegistry.counter("app.file.metadata.purged").increment();
    }
    
    @Transactional
    public void cleanupStaleFile(UUID fileId) {
        StoredFile storedFile = storedFileRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> new FileNotFoundException(fileId));
        storedFile.setDeleted(true);
        storedFile.setStatus(StoredFileStatus.DELETED);
        storedFile.setDeletedAt(Instant.now());
        storedFileRepository.save(storedFile);
        fileStorageService.delete(storedFile.getBucketName(), storedFile.getObjectKey());
        meterRegistry.counter("app.file.cleanup.deleted", "status", storedFile.getStatus().name()).increment();
    }
    
    @Transactional(readOnly = true)
    public StoredFileResponse getMetadataInternal(UUID fileId) {
        return storedFileMapper.toResponse(requireExistingFile(fileId));
    }

    @Transactional
    public FileDownload downloadInternal(UUID fileId) {
        StoredFile storedFile = requireExistingFile(fileId);
        assertDownloadAllowed(storedFile);
        storedFile.setLastAccessedAt(Instant.now());
        StoredFile saved = storedFileRepository.save(storedFile);
        FileStorageObject storageObject = fileStorageService.download(saved.getBucketName(), saved.getObjectKey());
        meterRegistry.counter("app.file.download.completed", "kind", saved.getFileKind().name()).increment();
        return new FileDownload(saved, storageObject);
    }

    @Transactional
    public FileDownload previewInternal(UUID fileId) {
        StoredFile storedFile = requireExistingFile(fileId);
        assertDownloadAllowed(storedFile);
        if (!isPreviewAvailable(storedFile.getContentType())) {
            throw new FilePreviewNotAvailableException(fileId);
        }
        storedFile.setLastAccessedAt(Instant.now());
        StoredFile saved = storedFileRepository.save(storedFile);
        FileStorageObject storageObject = fileStorageService.download(saved.getBucketName(), saved.getObjectKey());
        meterRegistry.counter("app.file.preview.completed", "kind", saved.getFileKind().name()).increment();
        return new FileDownload(saved, storageObject);
    }
    
    private StoredFile requireExistingFile(UUID fileId) {
        return storedFileRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> new FileNotFoundException(fileId));
    }

    private StoredFile requireAccessibleFile(UUID requesterId, UUID fileId) {
        StoredFile storedFile = requireExistingFile(fileId);
        
        if (storedFile.getAccess() == StoredFileAccess.PUBLIC || storedFile.getOwnerId().equals(requesterId)) {
            return storedFile;
        }
        
        throw new FileNotFoundException(fileId);
    }
    
    private StoredFile requireOwnedFile(UUID requesterId, UUID fileId) {
        StoredFile storedFile = storedFileRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> new FileNotFoundException(fileId));
        
        if (storedFile.getOwnerId().equals(requesterId)) {
            return storedFile;
        }
        
        throw new FileNotFoundException(fileId);
    }
    
    private StoredFileStatus initialStatus(StoredFileKind fileKind) {
        if (fileScanProperties.isEnabled()) {
            return StoredFileStatus.PENDING_SCAN;
        }
        return fileKind == StoredFileKind.AVATAR
                ? StoredFileStatus.UPLOADED
                : StoredFileStatus.ACTIVE;
    }
    
    private StoredFileStatus postScanStatus(StoredFile storedFile) {
        return storedFile.getFileKind() == StoredFileKind.AVATAR
                ? StoredFileStatus.UPLOADED
                : StoredFileStatus.READY;
    }
    
    private void assertDownloadAllowed(StoredFile storedFile) {
        if (storedFile.getStatus() == StoredFileStatus.PENDING_SCAN) {
            throw new FilePendingScanException(storedFile.getId());
        }
        if (storedFile.getStatus() == StoredFileStatus.REJECTED) {
            throw new FileRejectedException(storedFile.getId());
        }
    }
    
    private void assertReadyForActivation(StoredFile storedFile) {
        if (storedFile.getStatus() == StoredFileStatus.PENDING_SCAN) {
            throw new FilePendingScanException(storedFile.getId());
        }
        if (storedFile.getStatus() == StoredFileStatus.REJECTED) {
            throw new FileRejectedException(storedFile.getId());
        }
        if (storedFile.getStatus() == StoredFileStatus.DELETED) {
            throw new InvalidFileLifecycleTransitionException(storedFile.getId(), storedFile.getStatus(), "ACTIVE");
        }
    }
    
    private void demoteOtherActiveAvatars(UUID ownerId, UUID activeFileId) {
        storedFileRepository.findAllByOwnerIdAndFileKindAndDeletedFalseOrderByCreatedAtDesc(ownerId, StoredFileKind.AVATAR)
                .stream()
                .filter(file -> !file.getId().equals(activeFileId))
                .filter(file -> file.getStatus() == StoredFileStatus.ACTIVE)
                .forEach(file -> {
                    file.setStatus(StoredFileStatus.ORPHANED);
                    storedFileRepository.save(file);
                });
    }
    
    private boolean isPreviewAvailable(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.equals("application/pdf") || normalized.startsWith("image/");
    }
    
    public record FileDownload(StoredFile metadata, FileStorageObject storageObject) {
    }
}
