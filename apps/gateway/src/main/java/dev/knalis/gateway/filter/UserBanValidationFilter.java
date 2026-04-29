package dev.knalis.gateway.filter;

import dev.knalis.gateway.service.BanStateService;
import dev.knalis.gateway.service.GatewayErrorResponseWriter;
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
public class UserBanValidationFilter implements GlobalFilter, Ordered {

    private final BanStateService banStateService;
    private final GatewayErrorResponseWriter errorResponseWriter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
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
        return Mono.fromCallable(() -> banStateService.findActiveBan(userId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(state -> state.map(activeBan -> errorResponseWriter.write(
                                exchange,
                                HttpStatus.FORBIDDEN,
                                activeBan.reason() != null ? "User is banned: " + activeBan.reason() : "User is banned"
                        ))
                        .orElseGet(() -> chain.filter(exchange)));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
