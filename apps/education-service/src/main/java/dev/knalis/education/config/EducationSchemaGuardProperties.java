package dev.knalis.education.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.education.schema-guard")
public class EducationSchemaGuardProperties {

    private boolean enabled = true;
    private boolean autoRepair = false;
    private String schema = "education";
}
