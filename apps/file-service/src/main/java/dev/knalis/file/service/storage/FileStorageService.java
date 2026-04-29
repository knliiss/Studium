package dev.knalis.file.service.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    
    void upload(String bucketName, String objectKey, MultipartFile file, String contentType);
    
    FileStorageObject download(String bucketName, String objectKey);
    
    void delete(String bucketName, String objectKey);
}
