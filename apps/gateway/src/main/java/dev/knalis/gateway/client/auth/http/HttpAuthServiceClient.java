package dev.knalis.gateway.client.auth.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.gateway.client.auth.AuthServiceClient;
import dev.knalis.gateway.client.auth.dto.AdminUserStatsResponse;
import dev.knalis.gateway.client.auth.dto.UserDirectoryLookupRequest;
import dev.knalis.gateway.client.auth.dto.UserSummaryResponse;
import dev.knalis.gateway.exception.GatewayClientException;
import dev.knalis.gateway.filter.RequestIdFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Component
public class HttpAuthServiceClient implements AuthServiceClient {

    private static final ParameterizedTypeReference<List<UserSummaryResponse>> USER_SUMMARY_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient authServiceWebClient;
    private final ObjectMapper objectMapper;

    public HttpAuthServiceClient(
            @Qualifier("authServiceWebClient") WebClient authServiceWebClient,
            ObjectMapper objectMapper
    ) {
        this.authServiceWebClient = authServiceWebClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<AdminUserStatsResponse> getAdminStats(String bearerToken, String requestId) {
        return authServiceWebClient.get()
                .uri("/api/admin/users/statistics")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(AdminUserStatsResponse.class)
                .onErrorMap(exception -> translateException("Auth service request failed", exception));
    }

    @Override
    public Mono<List<UserSummaryResponse>> lookupUsers(String bearerToken, String requestId, List<UUID> userIds) {
        return authServiceWebClient.post()
                .uri("/api/auth/users/lookup")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .bodyValue(new UserDirectoryLookupRequest(userIds))
                .retrieve()
                .bodyToMono(USER_SUMMARY_LIST_TYPE)
                .onErrorMap(exception -> translateException("Auth service request failed", exception));
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
            // Use fallback message when downstream response is not JSON.
        }
        return fallbackMessage;
    }
}
