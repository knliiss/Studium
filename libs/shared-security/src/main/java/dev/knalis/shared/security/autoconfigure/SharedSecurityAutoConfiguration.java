package dev.knalis.shared.security.autoconfigure;

import dev.knalis.shared.security.jwt.JwtDecoderFactory;
import dev.knalis.shared.security.jwt.JwtGrantedAuthoritiesExtractor;
import dev.knalis.shared.security.jwt.ReactiveJwtAuthenticationConverter;
import dev.knalis.shared.security.jwt.ReactiveJwtDecoderFactory;
import dev.knalis.shared.security.jwt.ServletJwtAuthenticationConverter;
import dev.knalis.shared.security.keys.RsaPrivateKeyLoader;
import dev.knalis.shared.security.keys.RsaPublicKeyLoader;
import dev.knalis.shared.security.user.CurrentUserService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

@AutoConfiguration
public class SharedSecurityAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public RsaPublicKeyLoader rsaPublicKeyLoader(ResourceLoader resourceLoader) {
        return new RsaPublicKeyLoader(resourceLoader);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public RsaPrivateKeyLoader rsaPrivateKeyLoader(ResourceLoader resourceLoader) {
        return new RsaPrivateKeyLoader(resourceLoader);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public JwtDecoderFactory jwtDecoderFactory() {
        return new JwtDecoderFactory();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public ReactiveJwtDecoderFactory reactiveJwtDecoderFactory() {
        return new ReactiveJwtDecoderFactory();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public JwtGrantedAuthoritiesExtractor jwtGrantedAuthoritiesExtractor() {
        return new JwtGrantedAuthoritiesExtractor();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public ServletJwtAuthenticationConverter servletJwtAuthenticationConverter(
            JwtGrantedAuthoritiesExtractor jwtGrantedAuthoritiesExtractor
    ) {
        return new ServletJwtAuthenticationConverter(jwtGrantedAuthoritiesExtractor);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public ReactiveJwtAuthenticationConverter reactiveJwtAuthenticationConverter(
            JwtGrantedAuthoritiesExtractor jwtGrantedAuthoritiesExtractor
    ) {
        return new ReactiveJwtAuthenticationConverter(jwtGrantedAuthoritiesExtractor);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public CurrentUserService currentUserService() {
        return new CurrentUserService();
    }
}
