package dev.knalis.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.gateway.filter.RequestIdFilter;
import dev.knalis.shared.web.dto.ErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GatewayErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String message) {
        return write(exchange, status, defaultErrorCode(status), message);
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String errorCode, String message) {
        String requestId = exchange.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (requestId == null || requestId.isBlank()) {
            requestId = exchange.getRequest().getHeaders().getFirst(RequestIdFilter.REQUEST_ID_HEADER);
        }
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                errorCode,
                message,
                exchange.getRequest().getURI().getPath(),
                requestId,
                Map.of()
        );

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(response);
        } catch (Exception exception) {
            body = new byte[0];
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    private String defaultErrorCode(HttpStatus status) {
        if (status == HttpStatus.FORBIDDEN) {
            return "ACCESS_DENIED";
        }
        if (status == HttpStatus.UNAUTHORIZED) {
            return "UNAUTHORIZED";
        }
        if (status == HttpStatus.BAD_REQUEST) {
            return "VALIDATION_FAILED";
        }
        if (status == HttpStatus.NOT_FOUND) {
            return "ENTITY_NOT_FOUND";
        }
        if (status.is5xxServerError()) {
            return "DOWNSTREAM_ERROR";
        }
        return "GATEWAY_ERROR";
    }
}
