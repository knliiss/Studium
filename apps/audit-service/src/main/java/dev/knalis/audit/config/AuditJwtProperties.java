package dev.knalis.audit.config;

import dev.knalis.shared.security.properties.JwtResourceServerProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.audit.jwt")
public class AuditJwtProperties extends JwtResourceServerProperties {
}
