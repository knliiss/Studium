package dev.knalis.file.service;

import org.springframework.stereotype.Component;

@Component
public class FileNameSanitizer {
    
    public String sanitize(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "file";
        }
        
        String normalized = fileName.replace("\\", "/");
        int lastSlash = normalized.lastIndexOf('/');
        String baseName = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        String cleaned = baseName.replaceAll("[\\p{Cntrl}]", "").trim();
        return cleaned.isBlank() ? "file" : cleaned;
    }
    
    public String safeExtension(String fileName) {
        String sanitized = sanitize(fileName);
        int dotIndex = sanitized.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == sanitized.length() - 1) {
            return "";
        }
        String extension = sanitized.substring(dotIndex).toLowerCase();
        return extension.matches("\\.[a-z0-9]{1,10}") ? extension : "";
    }
}
