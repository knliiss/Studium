package dev.knalis.shared.security.jwt;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class ServletJwtAuthenticationConverter implements Converter<Jwt, JwtAuthenticationToken> {
    
    private final JwtGrantedAuthoritiesExtractor jwtGrantedAuthoritiesExtractor;
    
    public ServletJwtAuthenticationConverter(JwtGrantedAuthoritiesExtractor jwtGrantedAuthoritiesExtractor) {
        this.jwtGrantedAuthoritiesExtractor = jwtGrantedAuthoritiesExtractor;
    }
    
    @Override
    public JwtAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(
                jwt,
                jwtGrantedAuthoritiesExtractor.extract(jwt),
                jwt.getSubject()
        );
    }
}
