package dev.knalis.telegrambot.bot.service;

import dev.knalis.telegrambot.bot.model.BotLocale;
import dev.knalis.telegrambot.bot.model.BotRequestContext;
import dev.knalis.telegrambot.bot.model.BotScreen;
import dev.knalis.telegrambot.bot.model.BotUserContext;
import dev.knalis.telegrambot.bot.model.BotUserRole;
import dev.knalis.telegrambot.client.NotificationTelegramInternalClient;
import dev.knalis.telegrambot.config.TelegramBotProperties;
import dev.knalis.telegrambot.dto.InternalTelegramConnectResponse;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.time.LocalDate;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudiumBotUxServiceHtmlSafetyTest {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<\\/?([a-zA-Z0-9-]+)(?:\\s+[^>]*)?>");
    private static final Set<String> ALLOWED_TAGS = Set.of("b", "i", "code");

    @Test
    void shouldReturnSafeInstructionsForStartWithoutToken() {
        StudiumBotUxService service = serviceWith("http://localhost:3000", null);

        BotScreen screen = service.start(testRequestContext("<tester&user>"), "/start");

        assertNotNull(screen);
        assertNotNull(screen.textHtml());
        assertTrue(screen.textHtml().contains("Підключити Telegram"));
        assertTrue(screen.textHtml().contains("локальному середовищі"));
        assertFalse(screen.textHtml().contains("<token>"));
        assertOnlyAllowedHtmlTags(screen.textHtml());
    }

    @Test
    void shouldUseSafeHtmlForInvalidTokenResponse() {
        NotificationTelegramInternalClient client = mock(NotificationTelegramInternalClient.class);
        when(client.connect(any())).thenReturn(new InternalTelegramConnectResponse("TOKEN_INVALID", "telegram.token_invalid"));
        StudiumBotUxService service = serviceWith("https://studium.example.com", client);

        BotScreen screen = service.start(testRequestContext("tester"), "/start invalid-token");

        assertNotNull(screen);
        assertNotNull(screen.textHtml());
        assertTrue(screen.textHtml().contains("недійсне"));
        assertFalse(screen.textHtml().contains("<token>"));
        assertOnlyAllowedHtmlTags(screen.textHtml());
    }

    @Test
    void shouldEscapeDynamicUsernameInMenuCaption() {
        StudiumBotUxService service = serviceWith("https://studium.example.com", null);

        BotScreen screen = service.mainMenu(testRequestContext("<admin&owner>"), false);

        assertNotNull(screen);
        assertNotNull(screen.captionHtml());
        assertTrue(screen.captionHtml().contains("@&lt;admin&amp;owner&gt;"));
        assertFalse(screen.captionHtml().contains("@<admin&owner>"));
        assertOnlyAllowedHtmlTags(screen.captionHtml());
    }

    @Test
    void shouldKeepTokenPlaceholderPlainTextInHelpMessage() {
        StudiumBotUxService service = serviceWith("https://studium.example.com", null);

        BotScreen screen = service.help(testRequestContext("tester"));

        assertNotNull(screen);
        assertNotNull(screen.textHtml());
        assertTrue(screen.textHtml().contains("/start TOKEN"));
        assertFalse(screen.textHtml().contains("<token>"));
        assertOnlyAllowedHtmlTags(screen.textHtml());
    }

    private StudiumBotUxService serviceWith(String frontendBaseUrl, NotificationTelegramInternalClient clientOverride) {
        TelegramBotProperties properties = new TelegramBotProperties();
        properties.setFrontendBaseUrl(frontendBaseUrl);

        BotLinkFactory botLinkFactory = new BotLinkFactory(properties, new TelegramUrlPolicy());
        NotificationTelegramInternalClient client = clientOverride == null
                ? mock(NotificationTelegramInternalClient.class)
                : clientOverride;
        BotImageRenderer botImageRenderer = new BotImageRenderer() {
            @Override
            public dev.knalis.telegrambot.bot.model.BotImage renderMainMenuBanner(long telegramUserId, BotLocale locale) {
                return new dev.knalis.telegrambot.bot.model.BotImage("banner.png", new byte[]{1});
            }

            @Override
            public dev.knalis.telegrambot.bot.model.BotImage renderScheduleDay(
                    long telegramUserId,
                    BotLocale locale,
                    LocalDate date,
                    dev.knalis.telegrambot.dto.InternalTelegramScheduleDayResponse scheduleDay
            ) {
                return new dev.knalis.telegrambot.bot.model.BotImage("schedule.png", new byte[]{1});
            }
        };

        return new StudiumBotUxService(
                client,
                new BotLocalizationService(),
                new BotHtmlEscaper(),
                botImageRenderer,
                botLinkFactory
        );
    }

    private BotRequestContext testRequestContext(String username) {
        BotUserContext userContext = new BotUserContext(
                100L,
                200L,
                username,
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

    private void assertOnlyAllowedHtmlTags(String html) {
        Matcher matcher = HTML_TAG_PATTERN.matcher(html);
        while (matcher.find()) {
            String tagName = matcher.group(1).toLowerCase();
            assertTrue(ALLOWED_TAGS.contains(tagName), "Unsupported HTML tag detected: " + tagName);
        }
    }
}
