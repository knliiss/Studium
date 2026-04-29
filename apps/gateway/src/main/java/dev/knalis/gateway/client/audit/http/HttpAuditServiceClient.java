package dev.knalis.gateway.client.audit.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.gateway.client.audit.AuditServiceClient;
import dev.knalis.gateway.client.audit.dto.AuditEventPageResponse;
import dev.knalis.gateway.exception.GatewayClientException;
import dev.knalis.gateway.filter.RequestIdFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Component
public class HttpAuditServiceClient implements AuditServiceClient {

    private final WebClient auditServiceWebClient;
    private final ObjectMapper objectMapper;

    public HttpAuditServiceClient(
            @Qualifier("auditServiceWebClient") WebClient auditServiceWebClient,
            ObjectMapper objectMapper
    ) {
        this.auditServiceWebClient = auditServiceWebClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<AuditEventPageResponse> getAuditEvents(
            String bearerToken,
            String requestId,
            int page,
            int size,
            String sortBy,
            String direction
    ) {
        return auditServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/audit")
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .queryParam("sortBy", sortBy)
                        .queryParam("direction", direction)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(AuditEventPageResponse.class)
                .onErrorMap(exception -> translateException("Audit service request failed", exception));
    }

    private GatewayClientException translateException(String fallbackMessage, Throwable exception) {
        if (exception instanceof GatewayClientException gatewayClientException) {
            return gatewayClientException;
        }
        if (exception instanceof WebClientResponseException webClientResponseException) {
            return new GatewayClientException(
                    resolveHttpStatus(webClientResponseException.getStatusCode().value()),
                    extractMessage(webClientResponseException.getResponseBodyAsString(), fallbackMessage)
            );
        }
        return new GatewayClientException(HttpStatus.BAD_GATEWAY, fallbackMessage);
    }

    private HttpStatus resolveHttpStatus(int statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode);
        return status != null ? status : HttpStatus.BAD_GATEWAY;
    }

    private String extractMessage(String responseBody, String fallbackMessage) {
        if (responseBody == null || responseBody.isBlank()) {
            return fallbackMessage;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode message = root.get("message");
            if (message != null && !message.asText().isBlank()) {
                return message.asText();
            }
        } catch (Exception ignored) {
        }
        return fallbackMessage;
    }
}
