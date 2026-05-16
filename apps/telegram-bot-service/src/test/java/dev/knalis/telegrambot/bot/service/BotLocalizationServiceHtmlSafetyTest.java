package dev.knalis.telegrambot.bot.service;

import dev.knalis.telegrambot.bot.model.BotLocale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class BotLocalizationServiceHtmlSafetyTest {

    private final BotLocalizationService botLocalizationService = new BotLocalizationService();

    @Test
    void shouldNotContainRawTokenPlaceholderInLocalizedMessages() {
        for (BotLocale locale : BotLocale.values()) {
            assertFalse(botLocalizationService.get(locale, "status.notConnected").contains("<token>"));
            assertFalse(botLocalizationService.get(locale, "help.text").contains("<token>"));
            assertFalse(botLocalizationService.get(locale, "connect.noToken").contains("<token>"));
            assertFalse(botLocalizationService.get(locale, "connect.tokenInvalid").contains("<token>"));
        }
    }
}
