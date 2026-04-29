package dev.knalis.shared.security.jwt;

import dev.knalis.shared.security.properties.JwtResourceServerProperties;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.interfaces.RSAPublicKey;

public class JwtDecoderFactory {
    
    public JwtDecoder create(RSAPublicKey publicKey, JwtResourceServerProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        
        var withIssuer = JwtValidators.createDefaultWithIssuer(properties.getIssuer());
        var withAudience = new JwtAudienceValidator(properties.getAudience());
        
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return decoder;
    }
}
