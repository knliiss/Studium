package dev.knalis.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        
        long startedAt = System.currentTimeMillis();
        
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getURI().getPath();
        String requestId = exchange.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        
        log.info("Incoming request: method={}, path={}, requestId={}", method, path, requestId);
        
        return chain.filter(exchange)
                .doOnSuccess(unused -> {
                    long duration = System.currentTimeMillis() - startedAt;
                    int status = exchange.getResponse().getStatusCode() != null
                            ? exchange.getResponse().getStatusCode().value()
                            : 0;
                    
                    log.info("Request completed: method={}, path={}, status={}, durationMs={}, requestId={}",
                            method, path, status, duration, requestId);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startedAt;
                    log.error("Request failed: method={}, path={}, durationMs={}, requestId={}, message={}",
                            method, path, duration, requestId, error.getMessage());
                });
    }
    
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
