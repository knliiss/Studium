package dev.knalis.file.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class FileBootstrapConfig {
    
    private final Runnable ensureBucketsExist;
    
    @Bean
    public ApplicationRunner fileStorageBootstrapRunner() {
        return args -> ensureBucketsExist.run();
    }
}
