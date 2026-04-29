package dev.knalis.file.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MinioConfig {
    
    private final FileStorageProperties properties;
    
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }
    
    @PostConstruct
    public void logStorageEndpoint() {
        log.info("Configured file storage endpoint={}", properties.getEndpoint());
    }
    
    @Bean
    public Runnable ensureBucketsExist(MinioClient minioClient) {
        return () -> {
            try {
                ensureBucket(minioClient, properties.getPublicBucket());
                ensureBucket(minioClient, properties.getPrivateBucket());
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to initialize object storage buckets", exception);
            }
        };
    }
    
    private void ensureBucket(MinioClient minioClient, String bucketName) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }
}
