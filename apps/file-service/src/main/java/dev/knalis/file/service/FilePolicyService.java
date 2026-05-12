package dev.knalis.file.service;

import dev.knalis.file.config.FileStorageProperties;
import dev.knalis.file.config.FileUploadProperties;
import dev.knalis.file.entity.StoredFileAccess;
import dev.knalis.file.entity.StoredFileKind;
import dev.knalis.file.exception.EmptyFileUploadException;
import dev.knalis.file.exception.FileContentTypeNotAllowedException;
import dev.knalis.file.exception.FileTooLargeException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class FilePolicyService {

    private static final Set<String> SAFE_OCTET_STREAM_EXTENSIONS = Set.of(
            "pdf",
            "png",
            "jpg",
            "jpeg",
            "webp",
            "gif",
            "svg",
            "txt",
            "md",
            "csv",
            "json",
            "xml",
            "yaml",
            "yml",
            "log",
            "java",
            "kt",
            "js",
            "jsx",
            "ts",
            "tsx",
            "html",
            "css",
            "scss",
            "sql",
            "py",
            "cs",
            "cpp",
            "c",
            "h",
            "php",
            "go",
            "rs",
            "sh",
            "bash",
            "zsh",
            "dockerfile",
            "gradle",
            "properties",
            "doc",
            "docx",
            "xls",
            "xlsx",
            "ppt",
            "pptx",
            "odt",
            "ods",
            "odp",
            "zip",
            "rar",
            "7z",
            "tar",
            "gz",
            "mp4",
            "webm",
            "mov",
            "mp3",
            "wav",
            "ogg"
    );
    
    private final FileUploadProperties fileUploadProperties;
    private final FileStorageProperties fileStorageProperties;
    
    public FilePolicyService(FileUploadProperties fileUploadProperties, FileStorageProperties fileStorageProperties) {
        this.fileUploadProperties = fileUploadProperties;
        this.fileStorageProperties = fileStorageProperties;
    }
    
    public void validateUpload(MultipartFile file, StoredFileKind fileKind) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new EmptyFileUploadException();
        }
        
        long maxSizeBytes = maxSizeBytes(fileKind);
        if (file.getSize() > maxSizeBytes) {
            throw new FileTooLargeException(file.getSize(), maxSizeBytes);
        }
        
        String contentType = normalizeContentType(file.getContentType());
        List<String> allowedContentTypes = allowedContentTypes(fileKind);
        if (!isContentTypeAllowed(file, contentType, allowedContentTypes)) {
            throw new FileContentTypeNotAllowedException(contentType, allowedContentTypes);
        }
    }
    
    public String contentType(MultipartFile file) {
        return normalizeContentType(file.getContentType());
    }
    
    public StoredFileAccess defaultAccess(StoredFileKind fileKind) {
        return StoredFileAccess.PRIVATE;
    }
    
    public String bucketName(StoredFileAccess access) {
        return access == StoredFileAccess.PUBLIC
                ? fileStorageProperties.getPublicBucket()
                : fileStorageProperties.getPrivateBucket();
    }
    
    private long maxSizeBytes(StoredFileKind fileKind) {
        return fileKind == StoredFileKind.AVATAR
                ? fileUploadProperties.getAvatarMaxSizeBytes()
                : fileUploadProperties.getGeneralMaxSizeBytes();
    }
    
    private List<String> allowedContentTypes(StoredFileKind fileKind) {
        return fileKind == StoredFileKind.AVATAR
                ? fileUploadProperties.getAllowedAvatarContentTypes()
                : fileUploadProperties.getAllowedGeneralContentTypes();
    }

    private boolean isContentTypeAllowed(
            MultipartFile file,
            String normalizedContentType,
            List<String> allowedContentTypes
    ) {
        List<String> normalizedAllowedTypes = allowedContentTypes.stream()
                .map(this::normalizeContentType)
                .toList();

        if (normalizedAllowedTypes.contains(normalizedContentType)) {
            if ("application/octet-stream".equals(normalizedContentType)) {
                return hasSafeOctetStreamExtension(file.getOriginalFilename());
            }
            return true;
        }

        return false;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }

        String trimmed = contentType.trim().toLowerCase(Locale.ROOT);
        int separatorIndex = trimmed.indexOf(';');
        if (separatorIndex >= 0) {
            return trimmed.substring(0, separatorIndex).trim();
        }
        return trimmed;
    }

    private boolean hasSafeOctetStreamExtension(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return false;
        }

        String normalizedName = originalFileName.trim().toLowerCase(Locale.ROOT);
        if ("dockerfile".equals(normalizedName)) {
            return true;
        }
        if (normalizedName.endsWith(".env.example")) {
            return true;
        }

        int extensionIndex = normalizedName.lastIndexOf('.');
        if (extensionIndex <= 0 || extensionIndex >= normalizedName.length() - 1) {
            return false;
        }

        String extension = normalizedName.substring(extensionIndex + 1);
        return SAFE_OCTET_STREAM_EXTENSIONS.contains(extension);
    }
}
