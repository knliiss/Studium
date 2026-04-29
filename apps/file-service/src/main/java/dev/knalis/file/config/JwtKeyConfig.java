package dev.knalis.file.config;

import dev.knalis.shared.security.keys.RsaPublicKeyLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.interfaces.RSAPublicKey;

@Configuration
@RequiredArgsConstructor
public class JwtKeyConfig {
    
    private final FileJwtProperties properties;
    private final RsaPublicKeyLoader rsaPublicKeyLoader;
    
    @Bean
    public RSAPublicKey rsaPublicKey() {
        return rsaPublicKeyLoader.load(properties.getPublicKeyPath());
    }
}
