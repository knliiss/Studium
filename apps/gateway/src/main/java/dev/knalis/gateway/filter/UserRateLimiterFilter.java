package dev.knalis.gateway.filter;

import dev.knalis.gateway.service.GatewayErrorResponseWriter;
import dev.knalis.gateway.service.UserRateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserRateLimiterFilter implements GlobalFilter, Ordered {

    private final UserRateLimiterService userRateLimiterService;
    private final GatewayErrorResponseWriter errorResponseWriter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!userRateLimiterService.isEnabled()) {
            return chain.filter(exchange);
        }

        return exchange.getPrincipal()
                .cast(Authentication.class)
                .flatMap(authentication -> validate(authentication, exchange, chain))
                .switchIfEmpty(chain.filter(exchange));
    }

    private Mono<Void> validate(Authentication authentication, ServerWebExchange exchange, GatewayFilterChain chain) {
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof Jwt jwt)) {
            return chain.filter(exchange);
        }

        UUID userId = UUID.fromString(jwt.getSubject());
        return Mono.fromCallable(() -> userRateLimiterService.tryAcquire(userId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(decision -> {
                    if (decision.allowed()) {
                        return chain.filter(exchange);
                    }
                    
                    exchange.getResponse().getHeaders().add("Retry-After", String.valueOf(decision.retryAfterSeconds()));
                    return errorResponseWriter.write(exchange, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 30;
    }
}
