package dev.knalis.gateway.config;

import dev.knalis.shared.security.jwt.ReactiveJwtDecoderFactory;
import dev.knalis.shared.security.keys.RsaPublicKeyLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

@Configuration
@RequiredArgsConstructor
public class JwtDecoderConfig {
    
    private final GatewayProperties gatewayProperties;
    private final RsaPublicKeyLoader rsaPublicKeyLoader;
    private final ReactiveJwtDecoderFactory reactiveJwtDecoderFactory;
    
    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        return reactiveJwtDecoderFactory.create(
                rsaPublicKeyLoader.load(gatewayProperties.getJwt().getPublicKeyPath()),
                gatewayProperties.getJwt()
        );
    }
}
