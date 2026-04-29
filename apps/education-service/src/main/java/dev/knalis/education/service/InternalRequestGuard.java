package dev.knalis.education.service;

import dev.knalis.education.config.EducationInternalProperties;
import dev.knalis.education.exception.InvalidInternalRequestException;
import org.springframework.stereotype.Service;

@Service
public class InternalRequestGuard {
    
    private final EducationInternalProperties educationInternalProperties;
    
    public InternalRequestGuard(EducationInternalProperties educationInternalProperties) {
        this.educationInternalProperties = educationInternalProperties;
    }
    
    public void verify(String sharedSecret) {
        if (sharedSecret == null || !sharedSecret.equals(educationInternalProperties.getSharedSecret())) {
            throw new InvalidInternalRequestException();
        }
    }
}
