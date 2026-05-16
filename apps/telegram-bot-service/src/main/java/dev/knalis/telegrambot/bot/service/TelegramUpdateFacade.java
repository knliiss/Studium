package dev.knalis.telegrambot.bot.service;

import dev.knalis.telegrambot.bot.model.BotRequestContext;
import dev.knalis.telegrambot.bot.model.BotScreen;
import dev.knalis.telegrambot.bot.routing.CallbackRouter;
import dev.knalis.telegrambot.bot.routing.CommandRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramUpdateFacade {

    private final BotContextService botContextService;
    private final CommandRouter commandRouter;
    private final CallbackRouter callbackRouter;
    private final BotResponseService botResponseService;
    private final StudiumBotUxService studiumBotUxService;

    public void handle(Update update, TelegramClient telegramClient) {
        BotRequestContext requestContext = botContextService.resolve(update);
        if (requestContext == null) {
            return;
        }

        try {
            BotScreen screen = routeUpdate(requestContext);
            if (screen == null) {
                return;
            }
            botResponseService.render(telegramClient, update, screen);
        } catch (Exception exception) {
            log.warn("Failed to handle Telegram update", exception);
            BotScreen fallback = studiumBotUxService.errorScreen(requestContext.userContext().locale(), true);
            botResponseService.render(telegramClient, update, fallback);
        }
    }

    private BotScreen routeUpdate(BotRequestContext requestContext) {
        Update update = requestContext.update();

        CallbackQuery callbackQuery = update.getCallbackQuery();
        if (callbackQuery != null && callbackQuery.getData() != null && !callbackQuery.getData().isBlank()) {
            BotScreen screen = callbackRouter.route(requestContext, callbackQuery.getData());
            return screen == null ? studiumBotUxService.mainMenu(requestContext, true) : screen;
        }

        Message message = update.getMessage();
        if (message == null || message.getText() == null || message.getText().isBlank()) {
            return null;
        }
        String text = message.getText().trim();
        if (!text.startsWith("/")) {
            return null;
        }

        BotScreen screen = commandRouter.route(requestContext, text);
        return screen == null ? studiumBotUxService.help(requestContext) : screen;
    }
}
