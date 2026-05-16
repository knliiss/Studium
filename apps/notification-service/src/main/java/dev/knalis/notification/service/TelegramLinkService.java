package dev.knalis.notification.service;

import dev.knalis.notification.config.TelegramProperties;
import dev.knalis.notification.dto.request.telegram.UpdateTelegramPreferencesRequest;
import dev.knalis.notification.dto.response.telegram.TelegramConnectTokenResponse;
import dev.knalis.notification.dto.response.telegram.TelegramLinkStatusResponse;
import dev.knalis.notification.dto.response.telegram.TelegramPreferencesResponse;
import dev.knalis.notification.entity.TelegramConnectToken;
import dev.knalis.notification.entity.TelegramConnectTokenStatus;
import dev.knalis.notification.entity.TelegramLink;
import dev.knalis.notification.repository.TelegramConnectTokenRepository;
import dev.knalis.notification.repository.TelegramLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramLinkService {

    public enum StartResult {
        CONNECTED,
        ALREADY_CONNECTED,
        USER_ALREADY_HAS_LINK,
        LINKED_TO_ANOTHER_ACCOUNT,
        TOKEN_INVALID,
        TOKEN_EXPIRED,
        TOKEN_USED,
        TOKEN_REVOKED
    }

    private final TelegramProperties telegramProperties;
    private final TelegramTokenCodec tokenCodec;
    private final TelegramLinkRepository telegramLinkRepository;
    private final TelegramConnectTokenRepository telegramConnectTokenRepository;
    private final TelegramBotApiClient telegramBotApiClient;

    @Transactional(readOnly = true)
    public TelegramLinkStatusResponse getStatus(UUID userId) {
        Optional<TelegramLink> link = telegramLinkRepository.findByUserIdAndActiveTrue(userId);
        Optional<TelegramConnectToken> pendingToken = Optional.empty();
        if (link.isEmpty()) {
            pendingToken = telegramConnectTokenRepository.findFirstByUserIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                    userId,
                    TelegramConnectTokenStatus.PENDING,
                    Instant.now()
            );
        }
        return toStatusResponse(
                link.orElse(null),
                pendingToken.isPresent(),
                null,
                pendingToken.map(TelegramConnectToken::getExpiresAt).orElse(null)
        );
    }

    @Transactional
    public TelegramConnectTokenResponse createConnectToken(UUID userId) {
        revokePendingTokens(userId);

        String rawToken = tokenCodec.generateRawToken();
        String tokenHash = tokenCodec.hashToken(rawToken);
        Instant expiresAt = Instant.now().plus(telegramProperties.getConnectTokenTtl());

        TelegramConnectToken token = new TelegramConnectToken();
        token.setUserId(userId);
        token.setTokenHash(tokenHash);
        token.setStatus(TelegramConnectTokenStatus.PENDING);
        token.setExpiresAt(expiresAt);
        telegramConnectTokenRepository.save(token);

        String deepLink = buildDeepLink(rawToken);
        return new TelegramConnectTokenResponse(rawToken, deepLink, expiresAt, isTelegramAvailable());
    }

    @Transactional
    public TelegramLinkStatusResponse updatePreferences(UUID userId, UpdateTelegramPreferencesRequest request) {
        TelegramLink link = telegramLinkRepository.findByUserIdAndActiveTrue(userId).orElse(null);
        if (link == null) {
            return toStatusResponse(null, false, null, null);
        }

        if (request.telegramEnabled() != null) {
            link.setTelegramEnabled(request.telegramEnabled());
        }
        if (request.notifyAssignments() != null) {
            link.setNotifyAssignments(request.notifyAssignments());
        }
        if (request.notifyTests() != null) {
            link.setNotifyTests(request.notifyTests());
        }
        if (request.notifyGrades() != null) {
            link.setNotifyGrades(request.notifyGrades());
        }
        if (request.notifySchedule() != null) {
            link.setNotifySchedule(request.notifySchedule());
        }
        if (request.notifyMaterials() != null) {
            link.setNotifyMaterials(request.notifyMaterials());
        }
        if (request.notifySystem() != null) {
            link.setNotifySystem(request.notifySystem());
        }

        return toStatusResponse(telegramLinkRepository.save(link), false, null, null);
    }

    @Transactional
    public TelegramLinkStatusResponse disconnect(UUID userId) {
        Optional<TelegramLink> maybeLink = telegramLinkRepository.findByUserIdAndActiveTrue(userId);
        if (maybeLink.isPresent()) {
            TelegramLink link = maybeLink.get();
            link.setActive(false);
            link.setDisconnectedAt(Instant.now());
            telegramLinkRepository.save(link);
        }
        revokePendingTokens(userId);
        return toStatusResponse(null, false, null, null);
    }

    @Transactional
    public void sendTestMessage(UUID userId) {
        TelegramLink link = telegramLinkRepository.findByUserIdAndActiveTrue(userId).orElse(null);
        if (link == null) {
            throw new IllegalStateException("Telegram link is not connected");
        }
        if (!isTelegramAvailable()) {
            throw new IllegalStateException("Telegram integration is unavailable");
        }
        if (!link.isTelegramEnabled()) {
            throw new IllegalStateException("Telegram delivery is disabled");
        }
        try {
            telegramBotApiClient.sendMessage(link.getChatId(), "✅ Telegram підключено до Studium.");
            recordDeliverySuccess(link);
        } catch (RestClientException exception) {
            recordDeliveryFailure(link, exception.getClass().getSimpleName());
            throw new IllegalStateException("Failed to send Telegram test message", exception);
        }
    }

    @Transactional
    public StartResult consumeConnectToken(String rawToken, long telegramUserId, long chatId, String telegramUsername) {
        expireOldTokens();

        String tokenHash = tokenCodec.hashToken(rawToken);
        TelegramConnectToken token = telegramConnectTokenRepository.findByTokenHash(tokenHash).orElse(null);
        if (token == null) {
            return StartResult.TOKEN_INVALID;
        }

        if (token.getStatus() == TelegramConnectTokenStatus.USED) {
            return StartResult.TOKEN_USED;
        }
        if (token.getStatus() == TelegramConnectTokenStatus.REVOKED) {
            return StartResult.TOKEN_REVOKED;
        }
        if (token.getStatus() == TelegramConnectTokenStatus.EXPIRED || token.getExpiresAt().isBefore(Instant.now())) {
            token.setStatus(TelegramConnectTokenStatus.EXPIRED);
            telegramConnectTokenRepository.save(token);
            return StartResult.TOKEN_EXPIRED;
        }

        UUID userId = token.getUserId();

        TelegramLink existingForUser = telegramLinkRepository.findByUserIdAndActiveTrue(userId).orElse(null);
        if (existingForUser != null) {
            if (existingForUser.getTelegramUserId().equals(telegramUserId)) {
                existingForUser.setChatId(chatId);
                existingForUser.setTelegramUsername(telegramUsername);
                existingForUser.setLastSeenAt(Instant.now());
                telegramLinkRepository.save(existingForUser);
                markTokenUsed(token);
                return StartResult.ALREADY_CONNECTED;
            }
            return StartResult.USER_ALREADY_HAS_LINK;
        }

        TelegramLink linkedElsewhere = telegramLinkRepository.findByTelegramUserIdAndActiveTrue(telegramUserId).orElse(null);
        if (linkedElsewhere != null && !linkedElsewhere.getUserId().equals(userId)) {
            return StartResult.LINKED_TO_ANOTHER_ACCOUNT;
        }

        TelegramLink link = new TelegramLink();
        link.setUserId(userId);
        link.setTelegramUserId(telegramUserId);
        link.setChatId(chatId);
        link.setTelegramUsername(telegramUsername);
        link.setConnectedAt(Instant.now());
        link.setLastSeenAt(Instant.now());
        link.setActive(true);
        telegramLinkRepository.save(link);

        markTokenUsed(token);
        revokePendingTokens(userId);
        return StartResult.CONNECTED;
    }

    @Transactional(readOnly = true)
    public Optional<TelegramLink> findActiveLinkByUserId(UUID userId) {
        return telegramLinkRepository.findByUserIdAndActiveTrue(userId);
    }

    @Transactional(readOnly = true)
    public Optional<TelegramLink> findActiveLinkByTelegramUserId(long telegramUserId) {
        return telegramLinkRepository.findByTelegramUserIdAndActiveTrue(telegramUserId);
    }

    @Transactional
    public void deactivateOnDeliveryFailure(TelegramLink link) {
        link.setActive(false);
        link.setDisconnectedAt(Instant.now());
        telegramLinkRepository.save(link);
    }

    @Transactional
    public void recordDeliverySuccess(TelegramLink link) {
        link.setLastSeenAt(Instant.now());
        link.setLastDeliveredAt(Instant.now());
        link.setLastDeliveryFailure(null);
        link.setLastDeliveryFailureAt(null);
        link.setTelegramSentCount(link.getTelegramSentCount() + 1);
        telegramLinkRepository.save(link);
    }

    @Transactional
    public void recordDeliveryFailure(TelegramLink link, String failureReason) {
        link.setDeliveryFailureCount(link.getDeliveryFailureCount() + 1);
        link.setLastDeliveryFailureAt(Instant.now());
        link.setLastDeliveryFailure(failureReason == null ? "UNKNOWN" : trimFailureReason(failureReason));
        link.setActive(false);
        link.setDisconnectedAt(Instant.now());
        telegramLinkRepository.save(link);
    }

    @Transactional(readOnly = true)
    public boolean isTelegramAvailable() {
        return telegramProperties.isEnabled()
                && telegramProperties.getBotToken() != null
                && !telegramProperties.getBotToken().isBlank()
                && telegramProperties.getBotUsername() != null
                && !telegramProperties.getBotUsername().isBlank();
    }

    private void markTokenUsed(TelegramConnectToken token) {
        token.setStatus(TelegramConnectTokenStatus.USED);
        token.setUsedAt(Instant.now());
        telegramConnectTokenRepository.save(token);
    }

    private void revokePendingTokens(UUID userId) {
        telegramConnectTokenRepository.updateStatusByUserIdAndStatus(
                userId,
                TelegramConnectTokenStatus.PENDING,
                TelegramConnectTokenStatus.REVOKED,
                Instant.now()
        );
    }

    private void expireOldTokens() {
        telegramConnectTokenRepository.expirePendingTokens(
                TelegramConnectTokenStatus.PENDING,
                TelegramConnectTokenStatus.EXPIRED,
                Instant.now(),
                Instant.now()
        );
    }

    private String buildDeepLink(String token) {
        String username = telegramProperties.getBotUsername();
        if (username == null || username.isBlank()) {
            return null;
        }
        return "https://t.me/" + username + "?start=" + token;
    }

    private TelegramLinkStatusResponse toStatusResponse(
            TelegramLink link,
            boolean pending,
            String deepLink,
            Instant tokenExpiresAt
    ) {
        TelegramPreferencesResponse preferences = link == null
                ? new TelegramPreferencesResponse(true, true, true, true, true, true, true)
                : new TelegramPreferencesResponse(
                link.isTelegramEnabled(),
                link.isNotifyAssignments(),
                link.isNotifyTests(),
                link.isNotifyGrades(),
                link.isNotifySchedule(),
                link.isNotifyMaterials(),
                link.isNotifySystem()
        );

        return new TelegramLinkStatusResponse(
                telegramProperties.isEnabled(),
                isTelegramAvailable(),
                link != null,
                pending,
                telegramProperties.getBotUsername(),
                deepLink,
                tokenExpiresAt,
                link == null ? null : link.getTelegramUsername(),
                link == null ? null : link.getTelegramUserId(),
                link == null ? null : link.getChatId(),
                link == null ? null : link.getConnectedAt(),
                link == null ? null : link.getDisconnectedAt(),
                preferences
        );
    }

    private String trimFailureReason(String reason) {
        if (reason.length() <= 300) {
            return reason;
        }
        return reason.substring(0, 300);
    }
}
