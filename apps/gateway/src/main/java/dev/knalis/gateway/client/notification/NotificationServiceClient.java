package dev.knalis.gateway.client.notification;

import dev.knalis.gateway.client.notification.dto.NotificationPageResponse;
import dev.knalis.gateway.client.notification.dto.UnreadCountResponse;
import reactor.core.publisher.Mono;

public interface NotificationServiceClient {

    Mono<UnreadCountResponse> getUnreadCount(String bearerToken, String requestId);

    Mono<NotificationPageResponse> getNotifications(String bearerToken, String requestId, int page, int size);
}
