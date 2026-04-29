package dev.knalis.file.service;

import dev.knalis.file.entity.StoredFileKind;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Component
public class FileObjectKeyFactory {
    
    private static final DateTimeFormatter PATH_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM", Locale.ROOT);
    
    private final FileNameSanitizer fileNameSanitizer;
    
    public FileObjectKeyFactory(FileNameSanitizer fileNameSanitizer) {
        this.fileNameSanitizer = fileNameSanitizer;
    }
    
    public String newObjectKey(UUID ownerId, UUID fileId, StoredFileKind fileKind, String originalFileName) {
        String extension = fileNameSanitizer.safeExtension(originalFileName);
        String datePath = PATH_DATE_FORMATTER.format(Instant.now().atZone(ZoneOffset.UTC));
        return ownerId
                + "/"
                + fileKind.name().toLowerCase(Locale.ROOT)
                + "/"
                + datePath
                + "/"
                + fileId
                + extension;
    }
}
