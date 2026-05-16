package dev.knalis.telegrambot.bot.controller;

import dev.knalis.telegrambot.bot.annotation.BotCommand;
import dev.knalis.telegrambot.bot.annotation.BotCommandHandler;
import dev.knalis.telegrambot.bot.model.BotRequestContext;
import dev.knalis.telegrambot.bot.model.BotScreen;
import dev.knalis.telegrambot.bot.service.StudiumBotUxService;
import lombok.RequiredArgsConstructor;

@BotCommandHandler
@RequiredArgsConstructor
public class BotCommandController {

    private final StudiumBotUxService studiumBotUxService;

    @BotCommand("/start")
    public BotScreen start(BotRequestContext context, String commandText) {
        return studiumBotUxService.start(context, commandText);
    }

    @BotCommand("/menu")
    public BotScreen menu(BotRequestContext context) {
        return studiumBotUxService.mainMenu(context, false);
    }

    @BotCommand("/status")
    public BotScreen status(BotRequestContext context) {
        return studiumBotUxService.status(context);
    }

    @BotCommand("/help")
    public BotScreen help(BotRequestContext context) {
        return studiumBotUxService.help(context);
    }

    @BotCommand("/disconnect")
    public BotScreen disconnect(BotRequestContext context) {
        return studiumBotUxService.disconnectInfo(context);
    }
}
