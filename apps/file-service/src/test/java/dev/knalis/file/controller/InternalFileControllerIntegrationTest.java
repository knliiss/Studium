package dev.knalis.file.controller;

import dev.knalis.file.entity.StoredFile;
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
import java.util.Comparator;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class InternalFileControllerIntegrationTest {
    
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
    private static final String INTERNAL_SECRET = "test-internal-secret";
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
        registry.add("app.file.scan.enabled", () -> true);
        registry.add("app.file.internal.shared-secret", () -> INTERNAL_SECRET);
        registry.add("app.file.jwt.public-key-path", () ->
                Path.of("infra", "keys", "public.pem").toAbsolutePath().toUri().toString());
    }
    
    @AfterEach
    void tearDown() {
        storedFileRepository.deleteAll();
        reset(fileStorageService, ensureBucketsExist);
    }
    
    @Test
    void pendingScanFileRequiresInternalApprovalBeforeActivationAndDownload() throws Exception {
        UUID ownerId = UUID.randomUUID();
        byte[] payload = "avatar".getBytes();
        
        mockMvc.perform(multipart("/api/files")
                        .file(new MockMultipartFile("file", "avatar.png", MediaType.IMAGE_PNG_VALUE, payload))
                        .param("fileKind", "AVATAR")
                        .with(jwtFor(ownerId, "owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_SCAN"));
        
        StoredFile storedFile = latestStoredFile();
        
        mockMvc.perform(get("/api/files/{fileId}/download", storedFile.getId())
                        .with(jwtFor(ownerId, "owner")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("FILE_PENDING_SCAN"));
        
        mockMvc.perform(put("/api/files/{fileId}/lifecycle/active", storedFile.getId())
                        .with(jwtFor(ownerId, "owner")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("FILE_PENDING_SCAN"));
        
        mockMvc.perform(post("/internal/files/{fileId}/scan-result", storedFile.getId())
                        .header("X-Internal-Secret", INTERNAL_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clean":true,"message":"scanner clean"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPLOADED"));
        
        when(fileStorageService.download(anyString(), anyString()))
                .thenReturn(new FileStorageObject(
                        new ByteArrayInputStream(payload),
                        MediaType.IMAGE_PNG_VALUE,
                        payload.length
                ));
        
        mockMvc.perform(put("/api/files/{fileId}/lifecycle/active", storedFile.getId())
                        .with(jwtFor(ownerId, "owner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        
        mockMvc.perform(get("/api/files/{fileId}/download", storedFile.getId())
                        .with(jwtFor(ownerId, "owner")))
                .andExpect(status().isOk());
    }
    
    @Test
    void cleanupReportRequiresInternalSecret() throws Exception {
        mockMvc.perform(get("/internal/files/cleanup/report")
                        .header("X-Internal-Secret", "wrong-secret"))
                .andExpect(status().isUnauthorized());
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
