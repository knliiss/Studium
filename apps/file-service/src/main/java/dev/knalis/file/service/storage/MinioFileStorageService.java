package dev.knalis.file.service.storage;

import dev.knalis.file.exception.FileStorageException;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MinioFileStorageService implements FileStorageService {
    
    private final MinioClient minioClient;
    
    @Override
    public void upload(String bucketName, String objectKey, MultipartFile file, String contentType) {
        try (var inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception exception) {
            throw new FileStorageException("upload", "Failed to upload file to object storage", exception);
        }
    }
    
    @Override
    public FileStorageObject download(String bucketName, String objectKey) {
        try {
            var stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
            var inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
            return new FileStorageObject(inputStream, stat.contentType(), stat.size());
        } catch (Exception exception) {
            throw new FileStorageException("download", "Failed to download file from object storage", exception);
        }
    }
    
    @Override
    public void delete(String bucketName, String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception exception) {
            throw new FileStorageException("delete", "Failed to delete file from object storage", exception);
        }
    }
}
