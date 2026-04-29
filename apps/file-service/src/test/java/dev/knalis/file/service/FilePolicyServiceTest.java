package dev.knalis.file.service;

import dev.knalis.file.config.FileStorageProperties;
import dev.knalis.file.config.FileUploadProperties;
import dev.knalis.file.entity.StoredFileKind;
import dev.knalis.file.exception.FileContentTypeNotAllowedException;
import dev.knalis.file.exception.FileTooLargeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FilePolicyServiceTest {
    
    private FilePolicyService filePolicyService;
    
    @BeforeEach
    void setUp() {
        FileUploadProperties uploadProperties = new FileUploadProperties();
        uploadProperties.setAvatarMaxSizeBytes(1_024L);
        uploadProperties.setGeneralMaxSizeBytes(2_048L);
        
        FileStorageProperties storageProperties = new FileStorageProperties();
        storageProperties.setPublicBucket("public");
        storageProperties.setPrivateBucket("private");
        storageProperties.setEndpoint("http://localhost:9000");
        storageProperties.setAccessKey("minio");
        storageProperties.setSecretKey("minio123");
        
        filePolicyService = new FilePolicyService(uploadProperties, storageProperties);
    }
    
    @Test
    void validateUploadAllowsSupportedAvatar() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "avatar".getBytes(StandardCharsets.UTF_8)
        );
        
        assertDoesNotThrow(() -> filePolicyService.validateUpload(file, StoredFileKind.AVATAR));
    }
    
    @Test
    void validateUploadRejectsOversizedFile() {
        byte[] payload = new byte[3_000];
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", payload);
        
        assertThrows(FileTooLargeException.class, () -> filePolicyService.validateUpload(file, StoredFileKind.DOCUMENT));
    }
    
    @Test
    void validateUploadRejectsDisallowedContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "script.sh",
                "application/x-sh",
                "echo hi".getBytes(StandardCharsets.UTF_8)
        );
        
        assertThrows(FileContentTypeNotAllowedException.class, () -> filePolicyService.validateUpload(file, StoredFileKind.GENERIC));
    }
}
