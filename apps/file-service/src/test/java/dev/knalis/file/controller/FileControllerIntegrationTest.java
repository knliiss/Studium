package dev.knalis.file.controller;

import dev.knalis.file.entity.StoredFile;
import dev.knalis.file.entity.StoredFileStatus;
import dev.knalis.file.repository.StoredFileRepository;
import dev.knalis.file.service.storage.FileStorageObject;
import dev.knalis.file.service.storage.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class FileControllerIntegrationTest {
    
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private StoredFileRepository storedFileRepository;
    
    @MockitoBean
    private FileStorageService fileStorageService;
    
    @MockitoBean(name = "ensureBucketsExist")
    private Runnable ensureBucketsExist;
    
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("app.file.cleanup.enabled", () -> false);
        registry.add("app.file.jwt.public-key-path", () ->
                Path.of("infra", "keys", "public.pem").toAbsolutePath().toUri().toString());
        registry.add("app.file.internal.shared-secret", () -> "test-internal-secret");
        registry.add("app.file.quota.max-files-per-user", () -> 3L);
        registry.add("app.file.quota.max-avatar-files-per-user", () -> 1L);
        registry.add("app.file.quota.max-total-bytes-per-user", () -> 1024L * 1024L);
    }
    
    @AfterEach
    void tearDown() {
        storedFileRepository.deleteAll();
        reset(fileStorageService, ensureBucketsExist);
    }
    
    @Test
    void uploadPersistsMetadataAndDownloadUpdatesLastAccessedAt() throws Exception {
        UUID ownerId = UUID.randomUUID();
        byte[] payload = "avatar-content".getBytes();
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "avatar.png",
                MediaType.IMAGE_PNG_VALUE,
                payload
        );
        
        when(fileStorageService.download(anyString(), anyString()))
                .thenReturn(new FileStorageObject(
                        new ByteArrayInputStream(payload),
                        MediaType.IMAGE_PNG_VALUE,
                        payload.length
                ));
        
        mockMvc.perform(multipart("/api/files")
                        .file(multipartFile)
                        .param("fileKind", "AVATAR")
                        .with(jwtFor(ownerId, "owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(ownerId.toString()))
                .andExpect(jsonPath("$.fileKind").value("AVATAR"))
                .andExpect(jsonPath("$.status").value("UPLOADED"));
        
        StoredFile storedFile = latestStoredFile();
        Instant initialLastAccessedAt = storedFile.getLastAccessedAt();
        
        mockMvc.perform(get("/api/files/{fileId}", storedFile.getId())
                        .with(jwtFor(ownerId, "owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(storedFile.getId().toString()));
        
        mockMvc.perform(get("/api/files/{fileId}/download", storedFile.getId())
                        .with(jwtFor(ownerId, "owner")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("avatar.png")))
                .andExpect(content().contentType(MediaType.IMAGE_PNG_VALUE));
        
        StoredFile updatedStoredFile = storedFileRepository.findById(storedFile.getId()).orElseThrow();
        assertFalse(updatedStoredFile.getLastAccessedAt().isBefore(initialLastAccessedAt));
        verify(fileStorageService).download(updatedStoredFile.getBucketName(), updatedStoredFile.getObjectKey());
    }
    
    @Test
    void privateFileIsHiddenFromAnotherUser() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID anotherUserId = UUID.randomUUID();
        uploadAvatar(ownerId, "secret.png");
        StoredFile storedFile = latestStoredFile();
        
        mockMvc.perform(get("/api/files/{fileId}", storedFile.getId())
                        .with(jwtFor(anotherUserId, "intruder")))
                .andExpect(status().isNotFound());
        
        mockMvc.perform(get("/api/files/{fileId}/download", storedFile.getId())
                        .with(jwtFor(anotherUserId, "intruder")))
                .andExpect(status().isNotFound());
    }
    
    @Test
    void ownerCanMoveFileThroughLifecycleStates() throws Exception {
        UUID ownerId = UUID.randomUUID();
        uploadAvatar(ownerId, "lifecycle.png");
        StoredFile storedFile = latestStoredFile();
        
        mockMvc.perform(put("/api/files/{fileId}/lifecycle/active", storedFile.getId())
                        .with(jwtFor(ownerId, "owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        
        mockMvc.perform(put("/api/files/{fileId}/lifecycle/orphaned", storedFile.getId())
                        .with(jwtFor(ownerId, "owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ORPHANED"));
        
        StoredFile updatedStoredFile = storedFileRepository.findById(storedFile.getId()).orElseThrow();
        assertEquals(StoredFileStatus.ORPHANED, updatedStoredFile.getStatus());
    }
    
    @Test
    void avatarQuotaBlocksSecondAvatarUploadForSameUser() throws Exception {
        UUID ownerId = UUID.randomUUID();
        uploadAvatar(ownerId, "first.png");
        
        mockMvc.perform(multipart("/api/files")
                        .file(new MockMultipartFile(
                                "file",
                                "second.png",
                                MediaType.IMAGE_PNG_VALUE,
                                "second".getBytes()
                        ))
                        .param("fileKind", "AVATAR")
                        .with(jwtFor(ownerId, "owner")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("FILE_QUOTA_EXCEEDED"));
    }
    
    @Test
    void deleteMarksMetadataDeletedAndRemovesObject() throws Exception {
        UUID ownerId = UUID.randomUUID();
        uploadAvatar(ownerId, "delete.png");
        StoredFile storedFile = latestStoredFile();
        
        mockMvc.perform(delete("/api/files/{fileId}", storedFile.getId())
                        .with(jwtFor(ownerId, "owner")))
                .andExpect(status().isNoContent());
        
        StoredFile deletedStoredFile = storedFileRepository.findById(storedFile.getId()).orElseThrow();
        assertTrue(deletedStoredFile.isDeleted());
        assertEquals(StoredFileStatus.DELETED, deletedStoredFile.getStatus());
        assertFalse(storedFileRepository.findByIdAndDeletedFalse(storedFile.getId()).isPresent());
        verify(fileStorageService).delete(deletedStoredFile.getBucketName(), deletedStoredFile.getObjectKey());
    }
    
    private void uploadAvatar(UUID ownerId, String filename) throws Exception {
        mockMvc.perform(multipart("/api/files")
                        .file(new MockMultipartFile(
                                "file",
                                filename,
                                MediaType.IMAGE_PNG_VALUE,
                                "avatar".getBytes()
                        ))
                        .param("fileKind", "AVATAR")
                        .with(jwtFor(ownerId, "owner")))
                .andExpect(status().isOk());
    }
    
    private StoredFile latestStoredFile() {
        return storedFileRepository.findAll().stream()
                .max(Comparator.comparing(StoredFile::getCreatedAt))
                .orElseThrow();
    }
    
    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtFor(UUID userId, String username) {
        return jwt().jwt(jwt -> jwt
                .subject(userId.toString())
                .claim("username", username)
                .tokenValue("test-token"));
    }
}
