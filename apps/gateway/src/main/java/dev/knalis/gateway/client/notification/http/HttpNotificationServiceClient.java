package dev.knalis.gateway.client.notification.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.knalis.gateway.client.notification.NotificationServiceClient;
import dev.knalis.gateway.client.notification.dto.NotificationPageResponse;
import dev.knalis.gateway.client.notification.dto.UnreadCountResponse;
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
public class HttpNotificationServiceClient implements NotificationServiceClient {

    private final WebClient notificationServiceWebClient;
    private final ObjectMapper objectMapper;

    public HttpNotificationServiceClient(
            @Qualifier("notificationServiceWebClient") WebClient notificationServiceWebClient,
            ObjectMapper objectMapper
    ) {
        this.notificationServiceWebClient = notificationServiceWebClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<UnreadCountResponse> getUnreadCount(String bearerToken, String requestId) {
        return notificationServiceWebClient.get()
                .uri("/api/notifications/unread-count")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(UnreadCountResponse.class)
                .onErrorMap(exception -> translateException("Notification service request failed", exception));
    }

    @Override
    public Mono<NotificationPageResponse> getNotifications(String bearerToken, String requestId, int page, int size) {
        return notificationServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/notifications")
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(RequestIdFilter.REQUEST_ID_HEADER, requestId)
                .retrieve()
                .bodyToMono(NotificationPageResponse.class)
                .onErrorMap(exception -> translateException("Notification service request failed", exception));
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
