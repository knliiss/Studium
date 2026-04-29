package dev.knalis.shared.security.jwt;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

public class ReactiveJwtAuthenticationConverter implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {
    
    private final JwtGrantedAuthoritiesExtractor jwtGrantedAuthoritiesExtractor;
    
    public ReactiveJwtAuthenticationConverter(JwtGrantedAuthoritiesExtractor jwtGrantedAuthoritiesExtractor) {
        this.jwtGrantedAuthoritiesExtractor = jwtGrantedAuthoritiesExtractor;
    }
    
    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        return Mono.just(new JwtAuthenticationToken(
                jwt,
                jwtGrantedAuthoritiesExtractor.extract(jwt),
                jwt.getSubject()
        ));
    }
}
