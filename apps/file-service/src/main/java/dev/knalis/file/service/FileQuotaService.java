package dev.knalis.file.service;

import dev.knalis.file.config.FileQuotaProperties;
import dev.knalis.file.entity.StoredFileKind;
import dev.knalis.file.entity.StoredFileStatus;
import dev.knalis.file.exception.FileQuotaExceededException;
import dev.knalis.file.repository.StoredFileRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class FileQuotaService {
    
    private final FileQuotaProperties fileQuotaProperties;
    private final StoredFileRepository storedFileRepository;
    
    public FileQuotaService(FileQuotaProperties fileQuotaProperties, StoredFileRepository storedFileRepository) {
        this.fileQuotaProperties = fileQuotaProperties;
        this.storedFileRepository = storedFileRepository;
    }
    
    public void validateQuota(UUID ownerId, StoredFileKind fileKind, long newFileSizeBytes) {
        long currentFileCount = storedFileRepository.countByOwnerIdAndDeletedFalseAndStatusNot(
                ownerId,
                StoredFileStatus.REJECTED
        );
        if (currentFileCount >= fileQuotaProperties.getMaxFilesPerUser()) {
            throw new FileQuotaExceededException(
                    "maxFilesPerUser",
                    currentFileCount,
                    fileQuotaProperties.getMaxFilesPerUser()
            );
        }
        
        long currentTotalBytes = storedFileRepository.sumSizeBytesByOwnerIdExcludingStatus(ownerId, StoredFileStatus.REJECTED);
        if (currentTotalBytes + newFileSizeBytes > fileQuotaProperties.getMaxTotalBytesPerUser()) {
            throw new FileQuotaExceededException(
                    "maxTotalBytesPerUser",
                    currentTotalBytes + newFileSizeBytes,
                    fileQuotaProperties.getMaxTotalBytesPerUser()
            );
        }
        
        if (fileKind == StoredFileKind.AVATAR) {
            long currentAvatarCount = storedFileRepository.countByOwnerIdAndFileKindAndDeletedFalseAndStatusNot(
                    ownerId,
                    StoredFileKind.AVATAR,
                    StoredFileStatus.REJECTED
            );
            if (currentAvatarCount >= fileQuotaProperties.getMaxAvatarFilesPerUser()) {
                throw new FileQuotaExceededException(
                        "maxAvatarFilesPerUser",
                        currentAvatarCount,
                        fileQuotaProperties.getMaxAvatarFilesPerUser()
                );
            }
        }
    }
}
