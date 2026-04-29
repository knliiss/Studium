package dev.knalis.audit.config;

import dev.knalis.shared.security.keys.RsaPublicKeyLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.interfaces.RSAPublicKey;

@Configuration
public class JwtKeyConfig {

    @Bean
    public RSAPublicKey rsaPublicKey(AuditJwtProperties properties, RsaPublicKeyLoader loader) {
        return loader.load(properties.getPublicKeyPath());
    }
}
