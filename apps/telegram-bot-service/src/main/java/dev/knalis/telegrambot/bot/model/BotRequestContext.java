package dev.knalis.telegrambot.bot.model;

import org.telegram.telegrambots.meta.api.objects.Update;

public record BotRequestContext(
        Update update,
        BotUserContext userContext
) {
}
