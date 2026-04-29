package dev.knalis.shared.security.jwt;

import dev.knalis.shared.security.properties.JwtResourceServerProperties;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import java.security.interfaces.RSAPublicKey;

public class ReactiveJwtDecoderFactory {
    
    public ReactiveJwtDecoder create(RSAPublicKey publicKey, JwtResourceServerProperties properties) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withPublicKey(publicKey).build();
        
        var withIssuer = JwtValidators.createDefaultWithIssuer(properties.getIssuer());
        var withAudience = new JwtAudienceValidator(properties.getAudience());
        
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(withIssuer, withAudience));
        return decoder;
    }
}
