package dev.knalis.telegrambot.bot.model;

import lombok.Builder;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

@Builder
public record BotScreen(
        String textHtml,
        String captionHtml,
        BotImage image,
        InlineKeyboardMarkup keyboard,
        boolean preferEdit
) {
}
