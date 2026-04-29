package dev.knalis.shared.security.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JwtResourceServerProperties {
    
    @NotBlank
    private String issuer;
    
    @NotBlank
    private String audience;
    
    @NotBlank
    private String publicKeyPath;
}