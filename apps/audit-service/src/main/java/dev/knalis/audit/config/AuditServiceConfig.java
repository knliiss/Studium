package dev.knalis.audit.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        AuditJwtProperties.class,
        AuditInternalProperties.class
})
public class AuditServiceConfig {
}
