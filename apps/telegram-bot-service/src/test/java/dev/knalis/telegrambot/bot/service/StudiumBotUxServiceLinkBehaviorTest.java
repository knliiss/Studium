package dev.knalis.telegrambot.bot.service;

import dev.knalis.telegrambot.bot.model.BotLocale;
import dev.knalis.telegrambot.bot.model.BotRequestContext;
import dev.knalis.telegrambot.bot.model.BotScreen;
import dev.knalis.telegrambot.bot.model.BotUserContext;
import dev.knalis.telegrambot.bot.model.BotUserRole;
import dev.knalis.telegrambot.config.TelegramBotProperties;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudiumBotUxServiceLinkBehaviorTest {

    @Test
    void shouldSkipInvalidLocalUrlButtonAndUseFallbackCallback() {
        StudiumBotUxService service = serviceWithBaseUrl("http://localhost:3000");

        BotScreen screen = service.assignmentsStub(testRequestContext(), false);

        assertNotNull(screen);
        assertTrue(screen.textHtml().contains("Посилання на Studium недоступні в локальному середовищі."));
        assertTrue(hasNoUrlButtons(screen.keyboard()));
        assertTrue(hasCallbackPrefix(screen.keyboard(), "info:open:"));
    }

    @Test
    void shouldCreatePublicUrlButtonWhenFrontendUrlIsPublic() {
        StudiumBotUxService service = serviceWithBaseUrl("https://studium.example.com");

        BotScreen screen = service.assignmentsStub(testRequestContext(), false);

        assertNotNull(screen);
        assertEquals("https://studium.example.com/assignments", firstUrl(screen.keyboard()));
    }

    @Test
    void shouldNotFailToBuildHelpScreenInLocalEnvironment() {
        StudiumBotUxService service = serviceWithBaseUrl("http://localhost:3000");

        BotScreen screen = assertDoesNotThrow(() -> service.help(testRequestContext()));

        assertNotNull(screen);
        assertTrue(hasNoUrlButtons(screen.keyboard()));
    }

    private StudiumBotUxService serviceWithBaseUrl(String baseUrl) {
        TelegramBotProperties properties = new TelegramBotProperties();
        properties.setFrontendBaseUrl(baseUrl);

        BotLinkFactory botLinkFactory = new BotLinkFactory(properties, new TelegramUrlPolicy());
        BotImageRenderer botImageRenderer = new BotImageRenderer() {
            @Override
            public dev.knalis.telegrambot.bot.model.BotImage renderMainMenuBanner(long telegramUserId, BotLocale locale) {
                return new dev.knalis.telegrambot.bot.model.BotImage("banner.png", new byte[]{1});
            }

            @Override
            public dev.knalis.telegrambot.bot.model.BotImage renderScheduleDay(long telegramUserId, BotLocale locale, LocalDate date, dev.knalis.telegrambot.dto.InternalTelegramScheduleDayResponse scheduleDay) {
                return new dev.knalis.telegrambot.bot.model.BotImage("schedule.png", new byte[]{1});
            }
        };

        return new StudiumBotUxService(
                null,
                new BotLocalizationService(),
                new BotHtmlEscaper(),
                botImageRenderer,
                botLinkFactory
        );
    }

    private BotRequestContext testRequestContext() {
        BotUserContext userContext = new BotUserContext(
                100L,
                200L,
                "tester",
                "Test",
                "User",
                "uk",
                false,
                null,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                BotUserRole.UNKNOWN,
                BotLocale.UK
        );
        return new BotRequestContext(new Update(), userContext);
    }

    private boolean hasNoUrlButtons(InlineKeyboardMarkup keyboard) {
        return keyboard.getKeyboard().stream()
                .flatMap(row -> row.stream())
                .allMatch(button -> button.getUrl() == null || button.getUrl().isBlank());
    }

    private boolean hasCallbackPrefix(InlineKeyboardMarkup keyboard, String prefix) {
        return keyboard.getKeyboard().stream()
                .flatMap(row -> row.stream())
                .map(InlineKeyboardButton::getCallbackData)
                .anyMatch(data -> data != null && data.startsWith(prefix));
    }

    private String firstUrl(InlineKeyboardMarkup keyboard) {
        return keyboard.getKeyboard().stream()
                .flatMap(row -> row.stream())
                .map(InlineKeyboardButton::getUrl)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse(null);
    }
}
