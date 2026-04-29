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
            "application/pdf",
            "text/plain",
            "application/zip",
            "application/json"
    );
}
