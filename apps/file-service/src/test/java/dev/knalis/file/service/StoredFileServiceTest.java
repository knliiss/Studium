package dev.knalis.file.service;

import dev.knalis.file.config.FileScanProperties;
import dev.knalis.file.entity.StoredFile;
import dev.knalis.file.entity.StoredFileAccess;
import dev.knalis.file.entity.StoredFileKind;
import dev.knalis.file.entity.StoredFileStatus;
import dev.knalis.file.exception.FileNotFoundException;
import dev.knalis.file.exception.FilePendingScanException;
import dev.knalis.file.mapper.StoredFileMapper;
import dev.knalis.file.repository.StoredFileRepository;
import dev.knalis.file.service.storage.FileStorageObject;
import dev.knalis.file.service.storage.FileStorageService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoredFileServiceTest {
    
    @Mock
    private StoredFileRepository storedFileRepository;
    
    @Mock
    private StoredFileMapper storedFileMapper;
    
    @Mock
    private FilePolicyService filePolicyService;
    
    @Mock
    private FileQuotaService fileQuotaService;
    
    @Mock
    private FileStorageService fileStorageService;
    
    private FileScanProperties fileScanProperties;
    private StoredFileService storedFileService;
    
    @BeforeEach
    void setUp() {
        fileScanProperties = new FileScanProperties();
        storedFileService = new StoredFileService(
                storedFileRepository,
                storedFileMapper,
                filePolicyService,
                fileQuotaService,
                fileStorageService,
                new FileNameSanitizer(),
                new FileObjectKeyFactory(new FileNameSanitizer()),
                fileScanProperties,
                new SimpleMeterRegistry()
        );
    }
    
    @Test
    void getMetadataRejectsForeignPrivateFile() {
        UUID ownerId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        
        StoredFile storedFile = new StoredFile();
        storedFile.setId(fileId);
        storedFile.setOwnerId(ownerId);
        storedFile.setAccess(StoredFileAccess.PRIVATE);
        storedFile.setFileKind(StoredFileKind.GENERIC);
        
        when(storedFileRepository.findByIdAndDeletedFalse(fileId)).thenReturn(Optional.of(storedFile));
        
        assertThrows(FileNotFoundException.class, () -> storedFileService.getMetadata(requesterId, fileId));
    }
    
    @Test
    void downloadUpdatesLastAccessedAt() {
        UUID ownerId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        
        StoredFile storedFile = new StoredFile();
        storedFile.setId(fileId);
        storedFile.setOwnerId(ownerId);
        storedFile.setAccess(StoredFileAccess.PRIVATE);
        storedFile.setFileKind(StoredFileKind.GENERIC);
        storedFile.setBucketName("private");
        storedFile.setObjectKey("owner/file");
        storedFile.setOriginalFileName("file.txt");
        storedFile.setContentType("text/plain");
        storedFile.setSizeBytes(4L);
        storedFile.setCreatedAt(Instant.now());
        storedFile.setUpdatedAt(Instant.now());
        storedFile.setLastAccessedAt(Instant.now().minusSeconds(60));
        
        when(storedFileRepository.findByIdAndDeletedFalse(fileId)).thenReturn(Optional.of(storedFile));
        when(storedFileRepository.save(storedFile)).thenReturn(storedFile);
        when(fileStorageService.download("private", "owner/file"))
                .thenReturn(new FileStorageObject(new ByteArrayInputStream("test".getBytes()), "text/plain", 4L));
        
        StoredFileService.FileDownload download = storedFileService.download(ownerId, fileId);
        
        assertNotNull(download);
        verify(storedFileRepository).save(storedFile);
    }
    
    @Test
    void downloadRejectsPendingScanFile() {
        UUID ownerId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        
        StoredFile storedFile = new StoredFile();
        storedFile.setId(fileId);
        storedFile.setOwnerId(ownerId);
        storedFile.setAccess(StoredFileAccess.PRIVATE);
        storedFile.setStatus(StoredFileStatus.PENDING_SCAN);
        
        when(storedFileRepository.findByIdAndDeletedFalse(fileId)).thenReturn(Optional.of(storedFile));
        
        assertThrows(FilePendingScanException.class, () -> storedFileService.download(ownerId, fileId));
        verify(fileStorageService, never()).download(any(), any());
    }
}
