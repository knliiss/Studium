package dev.knalis.notification.controller;

import dev.knalis.notification.dto.request.internal.telegram.InternalTelegramConnectRequest;
import dev.knalis.notification.dto.request.internal.telegram.InternalTelegramContextRequest;
import dev.knalis.notification.dto.request.internal.telegram.InternalTelegramAdminUsersRequest;
import dev.knalis.notification.dto.request.internal.telegram.InternalTelegramBotUserActionRequest;
import dev.knalis.notification.dto.request.internal.telegram.InternalTelegramMarkAllReadRequest;
import dev.knalis.notification.dto.request.internal.telegram.InternalTelegramScheduleDayRequest;
import dev.knalis.notification.dto.request.internal.telegram.InternalTelegramStatusRequest;
import dev.knalis.notification.dto.request.internal.telegram.InternalTelegramTogglePreferenceRequest;
import dev.knalis.notification.dto.request.internal.telegram.InternalTelegramUnreadNotificationsRequest;
import dev.knalis.notification.dto.response.internal.telegram.InternalTelegramAdminUsersResponse;
import dev.knalis.notification.dto.response.internal.telegram.InternalTelegramBotStatsResponse;
import dev.knalis.notification.dto.response.internal.telegram.InternalTelegramConnectResponse;
import dev.knalis.notification.dto.response.internal.telegram.InternalTelegramConnectStatus;
import dev.knalis.notification.dto.response.internal.telegram.InternalTelegramContextResponse;
import dev.knalis.notification.dto.response.internal.telegram.InternalTelegramMarkAllReadResponse;
import dev.knalis.notification.dto.response.internal.telegram.InternalTelegramScheduleDayResponse;
import dev.knalis.notification.dto.response.internal.telegram.InternalTelegramStatusResponse;
import dev.knalis.notification.dto.response.internal.telegram.InternalTelegramTogglePreferenceResponse;
import dev.knalis.notification.dto.response.internal.telegram.InternalTelegramUnreadNotificationsResponse;
import dev.knalis.notification.service.InternalTelegramBotService;
import dev.knalis.notification.service.InternalRequestGuard;
import dev.knalis.notification.service.TelegramLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/telegram")
@RequiredArgsConstructor
public class InternalTelegramController {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final TelegramLinkService telegramLinkService;
    private final InternalTelegramBotService internalTelegramBotService;
    private final InternalRequestGuard internalRequestGuard;

    @PostMapping("/connect")
    public InternalTelegramConnectResponse connect(
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret,
            @Valid @RequestBody InternalTelegramConnectRequest request
    ) {
        internalRequestGuard.verify(sharedSecret);
        TelegramLinkService.StartResult result = telegramLinkService.consumeConnectToken(
                request.token(),
                request.telegramUserId(),
                request.chatId(),
                request.username()
        );
        return new InternalTelegramConnectResponse(toConnectStatus(result), toConnectMessage(result));
    }

    @PostMapping("/status")
    public InternalTelegramStatusResponse status(
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret,
            @Valid @RequestBody InternalTelegramStatusRequest request
    ) {
        internalRequestGuard.verify(sharedSecret);
        boolean connected = telegramLinkService.findActiveLinkByTelegramUserId(request.telegramUserId()).isPresent();
        return new InternalTelegramStatusResponse(connected, connected ? "CONNECTED" : "NOT_CONNECTED");
    }

    @PostMapping("/context")
    public InternalTelegramContextResponse context(
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret,
            @Valid @RequestBody InternalTelegramContextRequest request
    ) {
        internalRequestGuard.verify(sharedSecret);
        return internalTelegramBotService.resolveContext(
                request.telegramUserId(),
                request.chatId(),
                request.username()
        );
    }

    @PostMapping("/notifications/unread")
    public InternalTelegramUnreadNotificationsResponse unreadNotifications(
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret,
            @Valid @RequestBody InternalTelegramUnreadNotificationsRequest request
    ) {
        internalRequestGuard.verify(sharedSecret);
        return internalTelegramBotService.unreadNotifications(
                request.telegramUserId(),
                request.limit()
        );
    }

    @PostMapping("/notifications/mark-all-read")
    public InternalTelegramMarkAllReadResponse markAllRead(
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret,
            @Valid @RequestBody InternalTelegramMarkAllReadRequest request
    ) {
        internalRequestGuard.verify(sharedSecret);
        return internalTelegramBotService.markAllRead(request.telegramUserId());
    }

    @PostMapping("/schedule/day")
    public InternalTelegramScheduleDayResponse scheduleDay(
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret,
            @Valid @RequestBody InternalTelegramScheduleDayRequest request
    ) {
        internalRequestGuard.verify(sharedSecret);
        return internalTelegramBotService.scheduleDay(
                request.telegramUserId(),
                request.date()
        );
    }

    @PostMapping("/preferences/toggle")
    public InternalTelegramTogglePreferenceResponse togglePreference(
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret,
            @Valid @RequestBody InternalTelegramTogglePreferenceRequest request
    ) {
        internalRequestGuard.verify(sharedSecret);
        return internalTelegramBotService.togglePreference(
                request.telegramUserId(),
                request.category()
        );
    }

    @PostMapping("/admin/users")
    public InternalTelegramAdminUsersResponse adminUsers(
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret,
            @Valid @RequestBody InternalTelegramAdminUsersRequest request
    ) {
        internalRequestGuard.verify(sharedSecret);
        return internalTelegramBotService.adminUsers(
                request.page(),
                request.size()
        );
    }

    @GetMapping("/admin/stats")
    public InternalTelegramBotStatsResponse adminStats(
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret
    ) {
        internalRequestGuard.verify(sharedSecret);
        return internalTelegramBotService.botStats();
    }

    @PostMapping("/admin/users/disable")
    public void adminDisableUserLink(
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret,
            @Valid @RequestBody InternalTelegramBotUserActionRequest request
    ) {
        internalRequestGuard.verify(sharedSecret);
        internalTelegramBotService.disableLink(request.linkId());
    }

    @PostMapping("/admin/users/enable")
    public void adminEnableUserLink(
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret,
            @Valid @RequestBody InternalTelegramBotUserActionRequest request
    ) {
        internalRequestGuard.verify(sharedSecret);
        internalTelegramBotService.enableLink(request.linkId());
    }

    @PostMapping("/admin/users/send-test")
    public void adminSendTest(
            @RequestHeader(INTERNAL_SECRET_HEADER) String sharedSecret,
            @Valid @RequestBody InternalTelegramBotUserActionRequest request
    ) {
        internalRequestGuard.verify(sharedSecret);
        internalTelegramBotService.sendTest(request.linkId());
    }

    private InternalTelegramConnectStatus toConnectStatus(TelegramLinkService.StartResult result) {
        return switch (result) {
            case CONNECTED -> InternalTelegramConnectStatus.CONNECTED;
            case ALREADY_CONNECTED -> InternalTelegramConnectStatus.ALREADY_CONNECTED;
            case USER_ALREADY_HAS_LINK -> InternalTelegramConnectStatus.USER_ALREADY_HAS_LINK;
            case LINKED_TO_ANOTHER_ACCOUNT -> InternalTelegramConnectStatus.LINKED_TO_ANOTHER_ACCOUNT;
            case TOKEN_EXPIRED -> InternalTelegramConnectStatus.TOKEN_EXPIRED;
            case TOKEN_USED -> InternalTelegramConnectStatus.TOKEN_USED;
            case TOKEN_REVOKED -> InternalTelegramConnectStatus.TOKEN_REVOKED;
            case TOKEN_INVALID -> InternalTelegramConnectStatus.TOKEN_INVALID;
        };
    }

    private String toConnectMessage(TelegramLinkService.StartResult result) {
        return switch (result) {
            case CONNECTED -> "telegram.connected";
            case ALREADY_CONNECTED -> "telegram.already_connected";
            case USER_ALREADY_HAS_LINK -> "telegram.user_already_has_link";
            case LINKED_TO_ANOTHER_ACCOUNT -> "telegram.linked_to_another_account";
            case TOKEN_EXPIRED -> "telegram.token_expired";
            case TOKEN_USED -> "telegram.token_used";
            case TOKEN_REVOKED -> "telegram.token_revoked";
            case TOKEN_INVALID -> "telegram.token_invalid";
        };
    }
}
