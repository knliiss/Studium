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

@Service
public class FilePolicyService {
    
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
        if (!allowedContentTypes.contains(contentType)) {
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
    
    private String normalizeContentType(String contentType) {
        return contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType.trim().toLowerCase();
    }
}
