package dev.knalis.gateway.exception;

import dev.knalis.gateway.service.GatewayErrorResponseWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class GatewayAccessDeniedHandler implements ServerAccessDeniedHandler {
    
    private final GatewayErrorResponseWriter errorResponseWriter;
    
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return errorResponseWriter.write(
                exchange,
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "Access to the requested resource is denied"
        );
    }
}
