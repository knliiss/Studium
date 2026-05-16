package dev.knalis.telegrambot.bot.service;

import dev.knalis.telegrambot.bot.model.BotLocale;
import dev.knalis.telegrambot.bot.model.BotRequestContext;
import dev.knalis.telegrambot.bot.model.BotUserContext;
import dev.knalis.telegrambot.bot.model.BotUserRole;
import dev.knalis.telegrambot.client.NotificationTelegramInternalClient;
import dev.knalis.telegrambot.config.TelegramBotProperties;
import dev.knalis.telegrambot.dto.InternalTelegramContextRequest;
import dev.knalis.telegrambot.dto.InternalTelegramContextResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BotContextService {

    private final NotificationTelegramInternalClient notificationTelegramInternalClient;
    private final TelegramBotProperties telegramBotProperties;
    private final BotLocalizationService botLocalizationService;
    private volatile Set<Long> ownersCache;
    private volatile Set<Long> adminsCache;

    public BotRequestContext resolve(Update update) {
        User user = resolveUser(update);
        Long chatId = resolveChatId(update);
        if (user == null || user.getId() == null || chatId == null) {
            return null;
        }

        InternalTelegramContextResponse contextResponse = null;
        try {
            contextResponse = notificationTelegramInternalClient.context(
                    new InternalTelegramContextRequest(user.getId(), chatId, user.getUserName())
            );
        } catch (Exception exception) {
            log.warn("Failed to resolve Telegram context from notification-service");
        }

        BotUserRole role = resolveRole(user.getId(), contextResponse);
        BotLocale locale = botLocalizationService.resolveLocale(resolveLocaleCode(user, contextResponse));

        BotUserContext userContext = new BotUserContext(
                chatId,
                user.getId(),
                user.getUserName(),
                user.getFirstName(),
                user.getLastName(),
                user.getLanguageCode(),
                contextResponse != null && contextResponse.linked(),
                contextResponse == null ? null : contextResponse.userId(),
                contextResponse != null && contextResponse.telegramEnabled(),
                contextResponse == null || contextResponse.notifyAssignments(),
                contextResponse == null || contextResponse.notifyTests(),
                contextResponse == null || contextResponse.notifyGrades(),
                contextResponse == null || contextResponse.notifySchedule(),
                contextResponse == null || contextResponse.notifyMaterials(),
                contextResponse == null || contextResponse.notifySystem(),
                role,
                locale
        );
        return new BotRequestContext(update, userContext);
    }

    private BotUserRole resolveRole(Long telegramUserId, InternalTelegramContextResponse contextResponse) {
        if (ownerTelegramUserIds().contains(telegramUserId)) {
            return BotUserRole.OWNER;
        }
        if (adminTelegramUserIds().contains(telegramUserId)) {
            return BotUserRole.ADMIN;
        }
        if (contextResponse != null && contextResponse.linked()) {
            return BotUserRole.STUDENT;
        }
        return BotUserRole.UNKNOWN;
    }

    private String resolveLocaleCode(User user, InternalTelegramContextResponse contextResponse) {
        if (contextResponse != null && contextResponse.locale() != null && !contextResponse.locale().isBlank()) {
            return contextResponse.locale();
        }
        return user.getLanguageCode();
    }

    private Set<Long> ownerTelegramUserIds() {
        Set<Long> localCache = ownersCache;
        if (localCache != null) {
            return localCache;
        }
        ownersCache = parseIds(telegramBotProperties.getOwnerTelegramUserIds());
        return ownersCache;
    }

    private Set<Long> adminTelegramUserIds() {
        Set<Long> localCache = adminsCache;
        if (localCache != null) {
            return localCache;
        }
        adminsCache = parseIds(telegramBotProperties.getAdminTelegramUserIds());
        return adminsCache;
    }

    private Set<Long> parseIds(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(rawValue.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Long::parseLong)
                .collect(Collectors.toSet());
    }

    private User resolveUser(Update update) {
        Message message = update.getMessage();
        if (message != null) {
            return message.getFrom();
        }
        CallbackQuery callbackQuery = update.getCallbackQuery();
        if (callbackQuery != null) {
            return callbackQuery.getFrom();
        }
        return null;
    }

    private Long resolveChatId(Update update) {
        Message message = update.getMessage();
        if (message != null) {
            return message.getChatId();
        }
        CallbackQuery callbackQuery = update.getCallbackQuery();
        if (callbackQuery != null) {
            MaybeInaccessibleMessage callbackMessage = callbackQuery.getMessage();
            if (callbackMessage != null) {
                return callbackMessage.getChatId();
            }
        }
        return null;
    }
}
