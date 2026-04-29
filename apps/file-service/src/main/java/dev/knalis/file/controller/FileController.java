package dev.knalis.file.controller;

import dev.knalis.file.dto.response.StoredFileResponse;
import dev.knalis.file.entity.StoredFileKind;
import dev.knalis.file.service.StoredFileService;
import dev.knalis.shared.security.user.CurrentUserService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {
    
    private final StoredFileService storedFileService;
    private final CurrentUserService currentUserService;
    
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StoredFileResponse upload(
            Authentication authentication,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "GENERIC") StoredFileKind fileKind
    ) {
        return storedFileService.upload(
                currentUserService.getCurrentUserId(authentication),
                file,
                fileKind
        );
    }
    
    @GetMapping("/{fileId}")
    public StoredFileResponse getMetadata(
            Authentication authentication,
            @PathVariable @NotNull UUID fileId
    ) {
        return storedFileService.getMetadata(
                currentUserService.getCurrentUserId(authentication),
                fileId
        );
    }
    
    @GetMapping("/{fileId}/metadata")
    public StoredFileResponse getMetadataByAlias(
            Authentication authentication,
            @PathVariable @NotNull UUID fileId
    ) {
        return getMetadata(authentication, fileId);
    }
    
    @GetMapping("/{fileId}/download")
    public ResponseEntity<InputStreamResource> download(
            Authentication authentication,
            @PathVariable @NotNull UUID fileId
    ) {
        StoredFileService.FileDownload fileDownload = storedFileService.download(
                currentUserService.getCurrentUserId(authentication),
                fileId
        );
        
        MediaType mediaType = MediaType.parseMediaType(
                fileDownload.metadata().getContentType() != null
                        ? fileDownload.metadata().getContentType()
                        : MediaType.APPLICATION_OCTET_STREAM_VALUE
        );
        
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(mediaType)
                .contentLength(fileDownload.storageObject().sizeBytes())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(fileDownload.metadata().getOriginalFileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(new InputStreamResource(fileDownload.storageObject().stream()));
    }
    
    @GetMapping("/{fileId}/preview")
    public ResponseEntity<InputStreamResource> preview(
            Authentication authentication,
            @PathVariable @NotNull UUID fileId
    ) {
        StoredFileService.FileDownload fileDownload = storedFileService.preview(
                currentUserService.getCurrentUserId(authentication),
                fileId
        );
        
        MediaType mediaType = MediaType.parseMediaType(
                fileDownload.metadata().getContentType() != null
                        ? fileDownload.metadata().getContentType()
                        : MediaType.APPLICATION_OCTET_STREAM_VALUE
        );
        
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(mediaType)
                .contentLength(fileDownload.storageObject().sizeBytes())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(fileDownload.metadata().getOriginalFileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(new InputStreamResource(fileDownload.storageObject().stream()));
    }
    
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> delete(
            Authentication authentication,
            @PathVariable @NotNull UUID fileId
    ) {
        storedFileService.delete(
                currentUserService.getCurrentUserId(authentication),
                fileId
        );
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/mine")
    public List<StoredFileResponse> listMine(
            Authentication authentication,
            @RequestParam(required = false) StoredFileKind fileKind
    ) {
        return storedFileService.listMine(
                currentUserService.getCurrentUserId(authentication),
                fileKind
        );
    }
    
    @PutMapping("/{fileId}/lifecycle/active")
    public StoredFileResponse markActive(
            Authentication authentication,
            @PathVariable UUID fileId
    ) {
        return storedFileService.markActive(
                currentUserService.getCurrentUserId(authentication),
                fileId
        );
    }
    
    @PutMapping("/{fileId}/lifecycle/orphaned")
    public StoredFileResponse markOrphaned(
            Authentication authentication,
            @PathVariable UUID fileId
    ) {
        return storedFileService.markOrphaned(
                currentUserService.getCurrentUserId(authentication),
                fileId
        );
    }
}
