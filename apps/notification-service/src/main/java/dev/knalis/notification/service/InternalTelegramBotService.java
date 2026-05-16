package dev.knalis.notification.service;

import dev.knalis.notification.dto.response.NotificationResponse;
import dev.knalis.notification.dto.response.UnreadCountResponse;
import dev.knalis.notification.dto.response.internal.telegram.InternalTelegramAdminUserItemResponse;
import dev.knalis.notification.dto.response.internal.telegram.InternalTelegramAdminUsersResponse;
import dev.knalis.notification.dto.response.internal.telegram.InternalTelegramBotStatsResponse;
import dev.knalis.notification.dto.response.internal.telegram.InternalTelegramContextResponse;
import dev.knalis.notification.dto.response.internal.telegram.InternalTelegramMarkAllReadResponse;
import dev.knalis.notification.dto.response.internal.telegram.InternalTelegramScheduleDayResponse;
import dev.knalis.notification.dto.response.internal.telegram.InternalTelegramScheduleLessonItemResponse;
import dev.knalis.notification.dto.response.internal.telegram.InternalTelegramTogglePreferenceResponse;
import dev.knalis.notification.dto.response.internal.telegram.InternalTelegramUnreadNotificationItemResponse;
import dev.knalis.notification.dto.response.internal.telegram.InternalTelegramUnreadNotificationsResponse;
import dev.knalis.notification.entity.TelegramLink;
import dev.knalis.notification.repository.TelegramLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InternalTelegramBotService {

    private final TelegramLinkRepository telegramLinkRepository;
    private final NotificationService notificationService;
    private final TelegramLinkService telegramLinkService;

    @Transactional
    public InternalTelegramContextResponse resolveContext(long telegramUserId, long chatId, String username) {
        TelegramLink link = telegramLinkRepository.findByTelegramUserIdAndActiveTrue(telegramUserId).orElse(null);
        if (link == null) {
            return new InternalTelegramContextResponse(
                    false,
                    null,
                    false,
                    false,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    null
            );
        }

        if (!Long.valueOf(chatId).equals(link.getChatId()) || hasChangedUsername(username, link.getTelegramUsername())) {
            link.setChatId(chatId);
            link.setTelegramUsername(username);
            link.setLastSeenAt(Instant.now());
            telegramLinkRepository.save(link);
        }

        return new InternalTelegramContextResponse(
                true,
                link.getUserId(),
                link.isActive(),
                link.isTelegramEnabled(),
                link.isNotifyAssignments(),
                link.isNotifyTests(),
                link.isNotifyGrades(),
                link.isNotifySchedule(),
                link.isNotifyMaterials(),
                link.isNotifySystem(),
                null
        );
    }

    @Transactional(readOnly = true)
    public InternalTelegramUnreadNotificationsResponse unreadNotifications(long telegramUserId, int limit) {
        TelegramLink link = telegramLinkRepository.findByTelegramUserIdAndActiveTrue(telegramUserId).orElse(null);
        if (link == null) {
            return new InternalTelegramUnreadNotificationsResponse(List.of());
        }
        List<NotificationResponse> items = notificationService.getUnreadNotifications(link.getUserId(), limit);
        return new InternalTelegramUnreadNotificationsResponse(items.stream()
                .map(item -> new InternalTelegramUnreadNotificationItemResponse(
                        item.id(),
                        item.title(),
                        item.body(),
                        item.type().name(),
                        item.category().name(),
                        item.createdAt()
                ))
                .toList());
    }

    @Transactional
    public InternalTelegramMarkAllReadResponse markAllRead(long telegramUserId) {
        TelegramLink link = telegramLinkRepository.findByTelegramUserIdAndActiveTrue(telegramUserId).orElse(null);
        if (link == null) {
            return new InternalTelegramMarkAllReadResponse(0);
        }
        UnreadCountResponse unreadCountResponse = notificationService.markAllAsRead(link.getUserId());
        return new InternalTelegramMarkAllReadResponse(unreadCountResponse.unreadCount());
    }

    @Transactional(readOnly = true)
    public InternalTelegramScheduleDayResponse scheduleDay(long telegramUserId, LocalDate date) {
        TelegramLink link = telegramLinkRepository.findByTelegramUserIdAndActiveTrue(telegramUserId).orElse(null);
        if (link == null) {
            return new InternalTelegramScheduleDayResponse(date, false, List.of());
        }

        // Schedule-service internal contract is not available yet for Telegram context.
        return new InternalTelegramScheduleDayResponse(
                date,
                false,
                List.of()
        );
    }

    @Transactional(readOnly = true)
    public InternalTelegramAdminUsersResponse adminUsers(int page, int size) {
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), 20);
        Page<TelegramLink> links = telegramLinkRepository.findAllByOrderByConnectedAtDesc(
                PageRequest.of(boundedPage, boundedSize)
        );
        return new InternalTelegramAdminUsersResponse(
                links.getContent().stream()
                        .map(this::toAdminUserItemResponse)
                        .toList(),
                links.getNumber(),
                links.getSize(),
                links.getTotalElements(),
                links.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public InternalTelegramBotStatsResponse botStats() {
        return new InternalTelegramBotStatsResponse(
                telegramLinkRepository.count(),
                telegramLinkRepository.countByActiveTrue(),
                telegramLinkRepository.countByActiveFalse(),
                telegramLinkRepository.totalDeliveryFailureCount(),
                telegramLinkRepository.totalTelegramSentCount()
        );
    }

    @Transactional
    public void disableLink(UUID linkId) {
        TelegramLink link = telegramLinkRepository.findById(linkId)
                .orElseThrow(() -> new IllegalStateException("Telegram link not found"));
        if (!link.isActive()) {
            return;
        }
        link.setActive(false);
        link.setDisconnectedAt(Instant.now());
        telegramLinkRepository.save(link);
    }

    @Transactional
    public void enableLink(UUID linkId) {
        TelegramLink link = telegramLinkRepository.findById(linkId)
                .orElseThrow(() -> new IllegalStateException("Telegram link not found"));
        if (link.isActive()) {
            return;
        }

        TelegramLink activeByUser = telegramLinkRepository.findByUserIdAndActiveTrue(link.getUserId()).orElse(null);
        if (activeByUser != null && !activeByUser.getId().equals(link.getId())) {
            throw new IllegalStateException("Another active link already exists for the user");
        }
        TelegramLink activeByTelegram = telegramLinkRepository.findByTelegramUserIdAndActiveTrue(link.getTelegramUserId()).orElse(null);
        if (activeByTelegram != null && !activeByTelegram.getId().equals(link.getId())) {
            throw new IllegalStateException("Telegram account is active on another link");
        }

        link.setActive(true);
        link.setDisconnectedAt(null);
        telegramLinkRepository.save(link);
    }

    @Transactional
    public void sendTest(UUID linkId) {
        TelegramLink link = telegramLinkRepository.findById(linkId)
                .orElseThrow(() -> new IllegalStateException("Telegram link not found"));
        telegramLinkService.sendTestMessage(link.getUserId());
    }

    @Transactional
    public InternalTelegramTogglePreferenceResponse togglePreference(long telegramUserId, String category) {
        TelegramLink link = telegramLinkRepository.findByTelegramUserIdAndActiveTrue(telegramUserId)
                .orElseThrow(() -> new IllegalStateException("Active Telegram link not found"));

        switch (category) {
            case "assignments" -> link.setNotifyAssignments(!link.isNotifyAssignments());
            case "tests" -> link.setNotifyTests(!link.isNotifyTests());
            case "grades" -> link.setNotifyGrades(!link.isNotifyGrades());
            case "schedule" -> link.setNotifySchedule(!link.isNotifySchedule());
            case "materials" -> link.setNotifyMaterials(!link.isNotifyMaterials());
            case "system" -> link.setNotifySystem(!link.isNotifySystem());
            case "telegram" -> link.setTelegramEnabled(!link.isTelegramEnabled());
            default -> throw new IllegalStateException("Unsupported preference category");
        }
        TelegramLink savedLink = telegramLinkRepository.save(link);
        return new InternalTelegramTogglePreferenceResponse(
                savedLink.isTelegramEnabled(),
                savedLink.isNotifyAssignments(),
                savedLink.isNotifyTests(),
                savedLink.isNotifyGrades(),
                savedLink.isNotifySchedule(),
                savedLink.isNotifyMaterials(),
                savedLink.isNotifySystem()
        );
    }

    private boolean hasChangedUsername(String username, String persistedUsername) {
        if (username == null && persistedUsername == null) {
            return false;
        }
        if (username == null || persistedUsername == null) {
            return true;
        }
        return !username.equals(persistedUsername);
    }

    private InternalTelegramAdminUserItemResponse toAdminUserItemResponse(TelegramLink link) {
        return new InternalTelegramAdminUserItemResponse(
                link.getId(),
                link.getUserId(),
                null,
                link.getTelegramUsername(),
                link.getTelegramUserId(),
                link.getConnectedAt(),
                link.isActive(),
                link.getDisconnectedAt(),
                link.getLastDeliveryFailure()
        );
    }
}
