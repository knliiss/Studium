package dev.knalis.file.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.file.upload")
public class FileUploadProperties {
    
    @Positive
    private long avatarMaxSizeBytes = 5_242_880L;
    
    @Positive
    private long generalMaxSizeBytes = 20_971_520L;
    
    @NotEmpty
    private List<String> allowedAvatarContentTypes = List.of("image/jpeg", "image/png", "image/webp");
    
    @NotEmpty
    private List<String> allowedGeneralContentTypes = List.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/svg+xml",
            "application/pdf",
            "text/plain",
            "text/markdown",
            "text/csv",
            "application/zip",
            "application/x-zip-compressed",
            "application/x-rar-compressed",
            "application/x-7z-compressed",
            "application/gzip",
            "application/x-tar",
            "application/json",
            "application/xml",
            "text/xml",
            "application/yaml",
            "text/yaml",
            "application/x-yaml",
            "text/x-yaml",
            "text/html",
            "text/css",
            "text/javascript",
            "application/javascript",
            "application/typescript",
            "application/x-sh",
            "application/sql",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation",
            "video/mp4",
            "video/webm",
            "video/quicktime",
            "audio/mpeg",
            "audio/wav",
            "audio/ogg",
            "application/octet-stream"
    );
}
