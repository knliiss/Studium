package dev.knalis.file.service;

import dev.knalis.file.config.FileQuotaProperties;
import dev.knalis.file.entity.StoredFileKind;
import dev.knalis.file.entity.StoredFileStatus;
import dev.knalis.file.exception.FileQuotaExceededException;
import dev.knalis.file.repository.StoredFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileQuotaServiceTest {
    
    @Mock
    private StoredFileRepository storedFileRepository;
    
    private FileQuotaProperties fileQuotaProperties;
    private FileQuotaService fileQuotaService;
    
    @BeforeEach
    void setUp() {
        fileQuotaProperties = new FileQuotaProperties();
        fileQuotaProperties.setMaxFilesPerUser(2);
        fileQuotaProperties.setMaxAvatarFilesPerUser(1);
        fileQuotaProperties.setMaxTotalBytesPerUser(10);
        fileQuotaService = new FileQuotaService(fileQuotaProperties, storedFileRepository);
    }
    
    @Test
    void validateQuotaRejectsWhenFileCountLimitReached() {
        UUID ownerId = UUID.randomUUID();
        when(storedFileRepository.countByOwnerIdAndDeletedFalseAndStatusNot(ownerId, StoredFileStatus.REJECTED))
                .thenReturn(2L);
        
        assertThrows(FileQuotaExceededException.class, () ->
                fileQuotaService.validateQuota(ownerId, StoredFileKind.GENERIC, 1L));
    }
    
    @Test
    void validateQuotaRejectsWhenTotalSizeLimitWouldBeExceeded() {
        UUID ownerId = UUID.randomUUID();
        when(storedFileRepository.countByOwnerIdAndDeletedFalseAndStatusNot(ownerId, StoredFileStatus.REJECTED))
                .thenReturn(0L);
        when(storedFileRepository.sumSizeBytesByOwnerIdExcludingStatus(ownerId, StoredFileStatus.REJECTED))
                .thenReturn(9L);
        
        assertThrows(FileQuotaExceededException.class, () ->
                fileQuotaService.validateQuota(ownerId, StoredFileKind.GENERIC, 2L));
    }
    
    @Test
    void validateQuotaRejectsWhenAvatarQuotaReached() {
        UUID ownerId = UUID.randomUUID();
        when(storedFileRepository.countByOwnerIdAndDeletedFalseAndStatusNot(ownerId, StoredFileStatus.REJECTED))
                .thenReturn(0L);
        when(storedFileRepository.sumSizeBytesByOwnerIdExcludingStatus(ownerId, StoredFileStatus.REJECTED))
                .thenReturn(0L);
        when(storedFileRepository.countByOwnerIdAndFileKindAndDeletedFalseAndStatusNot(
                ownerId,
                StoredFileKind.AVATAR,
                StoredFileStatus.REJECTED
        ))
                .thenReturn(1L);
        
        assertThrows(FileQuotaExceededException.class, () ->
                fileQuotaService.validateQuota(ownerId, StoredFileKind.AVATAR, 1L));
    }
    
    @Test
    void validateQuotaAllowsUploadWithinLimits() {
        UUID ownerId = UUID.randomUUID();
        when(storedFileRepository.countByOwnerIdAndDeletedFalseAndStatusNot(ownerId, StoredFileStatus.REJECTED))
                .thenReturn(1L);
        when(storedFileRepository.sumSizeBytesByOwnerIdExcludingStatus(ownerId, StoredFileStatus.REJECTED))
                .thenReturn(5L);
        
        assertDoesNotThrow(() -> fileQuotaService.validateQuota(ownerId, StoredFileKind.GENERIC, 4L));
    }
}
