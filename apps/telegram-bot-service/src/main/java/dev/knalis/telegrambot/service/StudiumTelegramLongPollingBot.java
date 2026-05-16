package dev.knalis.telegrambot.service;

import dev.knalis.telegrambot.config.TelegramBotProperties;
import dev.knalis.telegrambot.config.TelegramBotPollingCondition;
import dev.knalis.telegrambot.bot.service.TelegramUpdateFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Slf4j
@Component
@RequiredArgsConstructor
@Conditional(TelegramBotPollingCondition.class)
public class StudiumTelegramLongPollingBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final TelegramBotProperties telegramBotProperties;
    private final TelegramUpdateFacade telegramUpdateFacade;

    private TelegramClient telegramClient;

    @AfterBotRegistration
    public void afterRegistration() {
        log.info("Telegram bot long polling registered successfully");
    }

    @Override
    public String getBotToken() {
        return telegramBotProperties.getBotToken();
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        try {
            telegramUpdateFacade.handle(update, client());
        } catch (Exception exception) {
            log.warn("Failed to handle Telegram update", exception);
        }
    }

    private TelegramClient client() {
        if (telegramClient == null) {
            telegramClient = new OkHttpTelegramClient(telegramBotProperties.getBotToken());
        }
        return telegramClient;
    }
}
