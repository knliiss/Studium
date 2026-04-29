package dev.knalis.shared.security.user;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public class CurrentUserService {
    
    public UUID getCurrentUserId(Authentication authentication) {
        return UUID.fromString(getJwt(authentication).getSubject());
    }
    
    public String getCurrentUsername(Authentication authentication) {
        Object username = getJwt(authentication).getClaim("username");
        return username != null ? username.toString() : "user";
    }
    
    public String getCurrentTokenValue(Authentication authentication) {
        return getJwt(authentication).getTokenValue();
    }
    
    private Jwt getJwt(Authentication authentication) {
        return (Jwt) authentication.getPrincipal();
    }
}
