package dev.knalis.education.config;

import dev.knalis.shared.security.properties.JwtResourceServerProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.education.jwt")
public class EducationJwtProperties extends JwtResourceServerProperties {
}
