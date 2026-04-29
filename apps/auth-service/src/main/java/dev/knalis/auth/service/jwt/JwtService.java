package dev.knalis.auth.service.jwt;

import dev.knalis.auth.config.AuthProperties;
import dev.knalis.auth.entity.Role;
import dev.knalis.auth.entity.User;
import dev.knalis.auth.mfa.entity.MfaMethodType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {
    
    private final JwtEncoder jwtEncoder;
    private final AuthProperties authProperties;
    
    public String generateAccessToken(User user) {
        return generateAccessToken(user, false, null);
    }
    
    public String generateAccessToken(User user, boolean mfaVerified, MfaMethodType mfaMethod) {
        Instant now = Instant.now();
        
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(authProperties.getJwt().getIssuer())
                .subject(user.getId().toString())
                .audience(List.of(authProperties.getJwt().getAudience()))
                .issuedAt(now)
                .expiresAt(now.plus(authProperties.getAccessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim("roles", user.getRoles().stream().map(Role::name).toList())
                .claim("username", user.getUsername())
                .claim("mfa", mfaVerified)
                .claim("amr", mfaMethod == null ? List.of("pwd") : List.of("pwd", mfaMethod.amrValue()))
                .build();
        
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
