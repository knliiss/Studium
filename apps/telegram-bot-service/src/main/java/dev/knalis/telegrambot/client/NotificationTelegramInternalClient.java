package dev.knalis.telegrambot.client;

import dev.knalis.telegrambot.config.TelegramBotProperties;
import dev.knalis.telegrambot.dto.InternalTelegramAdminUsersRequest;
import dev.knalis.telegrambot.dto.InternalTelegramAdminUsersResponse;
import dev.knalis.telegrambot.dto.InternalTelegramBotStatsResponse;
import dev.knalis.telegrambot.dto.InternalTelegramBotUserActionRequest;
import dev.knalis.telegrambot.dto.InternalTelegramConnectRequest;
import dev.knalis.telegrambot.dto.InternalTelegramConnectResponse;
import dev.knalis.telegrambot.dto.InternalTelegramContextRequest;
import dev.knalis.telegrambot.dto.InternalTelegramContextResponse;
import dev.knalis.telegrambot.dto.InternalTelegramMarkAllReadRequest;
import dev.knalis.telegrambot.dto.InternalTelegramMarkAllReadResponse;
import dev.knalis.telegrambot.dto.InternalTelegramNotificationsRequest;
import dev.knalis.telegrambot.dto.InternalTelegramNotificationsResponse;
import dev.knalis.telegrambot.dto.InternalTelegramScheduleDayRequest;
import dev.knalis.telegrambot.dto.InternalTelegramScheduleDayResponse;
import dev.knalis.telegrambot.dto.InternalTelegramStatusRequest;
import dev.knalis.telegrambot.dto.InternalTelegramStatusResponse;
import dev.knalis.telegrambot.dto.InternalTelegramTogglePreferenceRequest;
import dev.knalis.telegrambot.dto.InternalTelegramTogglePreferenceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class NotificationTelegramInternalClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final TelegramBotProperties telegramBotProperties;

    public InternalTelegramConnectResponse connect(InternalTelegramConnectRequest request) {
        return RestClient.create()
                .post()
                .uri(telegramBotProperties.getNotificationServiceBaseUrl() + "/internal/telegram/connect")
                .header(INTERNAL_SECRET_HEADER, telegramBotProperties.getInternalSecret())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(InternalTelegramConnectResponse.class);
    }

    public InternalTelegramStatusResponse status(InternalTelegramStatusRequest request) {
        return RestClient.create()
                .post()
                .uri(telegramBotProperties.getNotificationServiceBaseUrl() + "/internal/telegram/status")
                .header(INTERNAL_SECRET_HEADER, telegramBotProperties.getInternalSecret())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(InternalTelegramStatusResponse.class);
    }

    public InternalTelegramContextResponse context(InternalTelegramContextRequest request) {
        return RestClient.create()
                .post()
                .uri(telegramBotProperties.getNotificationServiceBaseUrl() + "/internal/telegram/context")
                .header(INTERNAL_SECRET_HEADER, telegramBotProperties.getInternalSecret())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(InternalTelegramContextResponse.class);
    }

    public InternalTelegramNotificationsResponse unreadNotifications(InternalTelegramNotificationsRequest request) {
        return RestClient.create()
                .post()
                .uri(telegramBotProperties.getNotificationServiceBaseUrl() + "/internal/telegram/notifications/unread")
                .header(INTERNAL_SECRET_HEADER, telegramBotProperties.getInternalSecret())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(InternalTelegramNotificationsResponse.class);
    }

    public InternalTelegramMarkAllReadResponse markAllNotificationsRead(InternalTelegramMarkAllReadRequest request) {
        return RestClient.create()
                .post()
                .uri(telegramBotProperties.getNotificationServiceBaseUrl() + "/internal/telegram/notifications/mark-all-read")
                .header(INTERNAL_SECRET_HEADER, telegramBotProperties.getInternalSecret())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(InternalTelegramMarkAllReadResponse.class);
    }

    public InternalTelegramScheduleDayResponse scheduleDay(InternalTelegramScheduleDayRequest request) {
        return RestClient.create()
                .post()
                .uri(telegramBotProperties.getNotificationServiceBaseUrl() + "/internal/telegram/schedule/day")
                .header(INTERNAL_SECRET_HEADER, telegramBotProperties.getInternalSecret())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(InternalTelegramScheduleDayResponse.class);
    }

    public InternalTelegramAdminUsersResponse adminUsers(InternalTelegramAdminUsersRequest request) {
        return RestClient.create()
                .post()
                .uri(telegramBotProperties.getNotificationServiceBaseUrl() + "/internal/telegram/admin/users")
                .header(INTERNAL_SECRET_HEADER, telegramBotProperties.getInternalSecret())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(InternalTelegramAdminUsersResponse.class);
    }

    public InternalTelegramBotStatsResponse adminStats() {
        return RestClient.create()
                .get()
                .uri(telegramBotProperties.getNotificationServiceBaseUrl() + "/internal/telegram/admin/stats")
                .header(INTERNAL_SECRET_HEADER, telegramBotProperties.getInternalSecret())
                .retrieve()
                .body(InternalTelegramBotStatsResponse.class);
    }

    public void adminDisableLink(InternalTelegramBotUserActionRequest request) {
        RestClient.create()
                .post()
                .uri(telegramBotProperties.getNotificationServiceBaseUrl() + "/internal/telegram/admin/users/disable")
                .header(INTERNAL_SECRET_HEADER, telegramBotProperties.getInternalSecret())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public void adminEnableLink(InternalTelegramBotUserActionRequest request) {
        RestClient.create()
                .post()
                .uri(telegramBotProperties.getNotificationServiceBaseUrl() + "/internal/telegram/admin/users/enable")
                .header(INTERNAL_SECRET_HEADER, telegramBotProperties.getInternalSecret())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public void adminSendTestMessage(InternalTelegramBotUserActionRequest request) {
        RestClient.create()
                .post()
                .uri(telegramBotProperties.getNotificationServiceBaseUrl() + "/internal/telegram/admin/users/send-test")
                .header(INTERNAL_SECRET_HEADER, telegramBotProperties.getInternalSecret())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public InternalTelegramTogglePreferenceResponse togglePreference(InternalTelegramTogglePreferenceRequest request) {
        return RestClient.create()
                .post()
                .uri(telegramBotProperties.getNotificationServiceBaseUrl() + "/internal/telegram/preferences/toggle")
                .header(INTERNAL_SECRET_HEADER, telegramBotProperties.getInternalSecret())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(InternalTelegramTogglePreferenceResponse.class);
    }
}
