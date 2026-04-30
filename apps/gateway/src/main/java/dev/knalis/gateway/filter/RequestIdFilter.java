package dev.knalis.gateway.filter;

import dev.knalis.gateway.config.GatewayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RequestIdFilter implements GlobalFilter, WebFilter, Ordered {
    
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_ATTRIBUTE = "requestId";

    private final GatewayProperties gatewayProperties;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange mutatedExchange = withRequestId(exchange);
        return chain.filter(mutatedExchange);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerWebExchange mutatedExchange = withRequestId(exchange);
        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private ServerWebExchange withRequestId(ServerWebExchange exchange) {
        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        
        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .header(REQUEST_ID_HEADER, requestId)
                .build();
        
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(request)
                .build();
        String resolvedRequestId = requestId;

        mutatedExchange.getAttributes().put(REQUEST_ID_ATTRIBUTE, resolvedRequestId);
        mutatedExchange.getResponse().beforeCommit(() -> {
            HttpHeaders responseHeaders = mutatedExchange.getResponse().getHeaders();
            responseHeaders.set(REQUEST_ID_HEADER, resolvedRequestId);
            mergeExposedHeaders(mutatedExchange);
            return Mono.empty();
        });
        return mutatedExchange;
    }

    private void mergeExposedHeaders(ServerWebExchange exchange) {
        if (!exchange.getRequest().getHeaders().containsKey(HttpHeaders.ORIGIN)) {
            return;
        }

        List<String> configuredExposedHeaders = gatewayProperties.getCors().getExposedHeaders();
        if (configuredExposedHeaders == null || configuredExposedHeaders.isEmpty()) {
            return;
        }

        HttpHeaders responseHeaders = exchange.getResponse().getHeaders();
        LinkedHashSet<String> mergedHeaders = new LinkedHashSet<>(responseHeaders.getAccessControlExposeHeaders());
        mergedHeaders.addAll(configuredExposedHeaders);
        responseHeaders.setAccessControlExposeHeaders(new ArrayList<>(mergedHeaders));
    }
}
