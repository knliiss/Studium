package dev.knalis.notification.controller;

import dev.knalis.notification.dto.request.telegram.UpdateTelegramPreferencesRequest;
import dev.knalis.notification.dto.response.telegram.TelegramConnectTokenResponse;
import dev.knalis.notification.dto.response.telegram.TelegramLinkStatusResponse;
import dev.knalis.notification.service.TelegramLinkService;
import dev.knalis.shared.security.user.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications/telegram")
@RequiredArgsConstructor
public class TelegramLinkController {

    private final TelegramLinkService telegramLinkService;
    private final CurrentUserService currentUserService;

    @GetMapping("/status")
    public TelegramLinkStatusResponse getStatus(Authentication authentication) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        return telegramLinkService.getStatus(userId);
    }

    @PostMapping("/connect-token")
    public TelegramConnectTokenResponse createConnectToken(Authentication authentication) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        return telegramLinkService.createConnectToken(userId);
    }

    @PostMapping("/disconnect")
    public TelegramLinkStatusResponse disconnect(Authentication authentication) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        return telegramLinkService.disconnect(userId);
    }

    @PostMapping("/test")
    public void sendTest(Authentication authentication) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        telegramLinkService.sendTestMessage(userId);
    }

    @PatchMapping("/preferences")
    public TelegramLinkStatusResponse updatePreferences(
            Authentication authentication,
            @RequestBody UpdateTelegramPreferencesRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId(authentication);
        return telegramLinkService.updatePreferences(userId, request);
    }
}
