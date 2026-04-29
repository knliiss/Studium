package dev.knalis.shared.security.jwt;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public class JwtAudienceValidator implements OAuth2TokenValidator<Jwt> {
    
    private final String expectedAudience;
    
    public JwtAudienceValidator(String expectedAudience) {
        this.expectedAudience = expectedAudience;
    }
    
    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> audience = token.getAudience();
        if (audience != null && audience.contains(expectedAudience)) {
            return OAuth2TokenValidatorResult.success();
        }
        
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Token audience is invalid", null)
        );
    }
}