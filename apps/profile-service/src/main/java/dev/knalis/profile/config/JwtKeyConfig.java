package dev.knalis.profile.config;

import dev.knalis.shared.security.keys.RsaPublicKeyLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.interfaces.RSAPublicKey;

@Configuration
public class JwtKeyConfig {
    
    @Bean
    public RSAPublicKey rsaPublicKey(ProfileJwtProperties properties, RsaPublicKeyLoader loader) {
        return loader.load(properties.getPublicKeyPath());
    }
}