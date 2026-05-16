package dev.knalis.notification.service;

import dev.knalis.notification.entity.Notification;
import dev.knalis.notification.entity.NotificationType;
import dev.knalis.notification.entity.TelegramLink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramNotificationDeliveryService {

    private final TelegramLinkService telegramLinkService;
    private final TelegramBotApiClient telegramBotApiClient;

    @Transactional
    public void deliver(Notification notification) {
        if (!telegramLinkService.isTelegramAvailable()) {
            return;
        }

        TelegramLink link = telegramLinkService.findActiveLinkByUserId(notification.getUserId()).orElse(null);
        if (link == null || !link.isTelegramEnabled()) {
            return;
        }

        if (!isAllowedByPreferences(link, notification.getType())) {
            return;
        }

        String text = "Studium\n\n" + notification.getTitle() + "\n" + notification.getBody();
        try {
            telegramBotApiClient.sendMessage(link.getChatId(), text);
            telegramLinkService.recordDeliverySuccess(link);
        } catch (RestClientException exception) {
            log.warn("Telegram delivery failed for notificationId={} userId={}", notification.getId(), notification.getUserId());
            telegramLinkService.recordDeliveryFailure(link, exception.getClass().getSimpleName());
        }
    }

    private boolean isAllowedByPreferences(TelegramLink link, NotificationType type) {
        return switch (type) {
            case ASSIGNMENT_CREATED, ASSIGNMENT_UPDATED, ASSIGNMENT_DEADLINE_REMINDER -> link.isNotifyAssignments();
            case TEST_PUBLISHED, TEST_DEADLINE_REMINDER -> link.isNotifyTests();
            case GRADE_ASSIGNED -> link.isNotifyGrades();
            case SCHEDULE_LESSON_CANCELLED,
                    SCHEDULE_LESSON_CHANGED,
                    SCHEDULE_EXTRA_LESSON,
                    SCHEDULE_ROOM_CHANGED,
                    SCHEDULE_FORMAT_CHANGED,
                    SCHEDULE_TEACHER_CHANGED -> link.isNotifySchedule();
            default -> link.isNotifySystem();
        };
    }
}
