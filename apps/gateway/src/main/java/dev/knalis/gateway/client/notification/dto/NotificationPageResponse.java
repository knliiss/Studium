package dev.knalis.gateway.client.notification.dto;

import java.util.List;

public record NotificationPageResponse(
        List<NotificationResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static NotificationPageResponse empty() {
        return new NotificationPageResponse(List.of(), 0, 0, 0, 0, true, true);
    }
}
