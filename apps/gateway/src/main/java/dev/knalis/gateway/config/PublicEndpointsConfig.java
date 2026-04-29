package dev.knalis.gateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PublicEndpointsConfig {
    
    private final GatewayProperties gatewayProperties;
    
    public List<String> getPublicEndpoints() {
        return gatewayProperties.getPublicEndpoints();
    }
}