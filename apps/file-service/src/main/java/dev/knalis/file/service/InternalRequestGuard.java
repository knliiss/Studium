package dev.knalis.file.service;

import dev.knalis.file.config.FileInternalProperties;
import dev.knalis.file.exception.InvalidInternalRequestException;
import org.springframework.stereotype.Service;

@Service
public class InternalRequestGuard {
    
    private final FileInternalProperties fileInternalProperties;
    
    public InternalRequestGuard(FileInternalProperties fileInternalProperties) {
        this.fileInternalProperties = fileInternalProperties;
    }
    
    public void verify(String sharedSecret) {
        if (sharedSecret == null || !sharedSecret.equals(fileInternalProperties.getSharedSecret())) {
            throw new InvalidInternalRequestException();
        }
    }
}
