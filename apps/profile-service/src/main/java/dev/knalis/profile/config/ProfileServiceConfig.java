package dev.knalis.profile.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ProfileJwtProperties.class, ProfileFileServiceProperties.class})
public class ProfileServiceConfig {
}
