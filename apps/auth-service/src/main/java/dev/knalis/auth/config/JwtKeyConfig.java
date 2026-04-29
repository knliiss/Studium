package dev.knalis.auth.config;

import dev.knalis.shared.security.keys.RsaPrivateKeyLoader;
import dev.knalis.shared.security.keys.RsaPublicKeyLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
@RequiredArgsConstructor
public class JwtKeyConfig {
    
    private final AuthProperties authProperties;
    private final RsaPrivateKeyLoader rsaPrivateKeyLoader;
    private final RsaPublicKeyLoader rsaPublicKeyLoader;
    
    @Bean
    public RSAPrivateKey rsaPrivateKey() {
        return rsaPrivateKeyLoader.load(authProperties.getJwt().getPrivateKeyPath());
    }
    
    @Bean
    public RSAPublicKey rsaPublicKey() {
        return rsaPublicKeyLoader.load(authProperties.getJwt().getPublicKeyPath());
    }
}
