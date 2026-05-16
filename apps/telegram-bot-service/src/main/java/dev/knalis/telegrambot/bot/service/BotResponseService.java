package dev.knalis.telegrambot.bot.service;

import dev.knalis.telegrambot.bot.model.BotImage;
import dev.knalis.telegrambot.bot.model.BotScreen;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.message.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;

@Slf4j
@Service
public class BotResponseService {

    private static final String HTML = "HTML";

    public void render(TelegramClient telegramClient, Update update, BotScreen screen) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        if (callbackQuery != null && callbackQuery.getId() != null) {
            answerCallbackSilently(telegramClient, callbackQuery.getId());
        }

        if (screen.preferEdit() && canEdit(update)) {
            try {
                editExistingMessage(telegramClient, update, screen);
                return;
            } catch (Exception exception) {
                log.warn("Failed to edit Telegram message, fallback to send new message");
            }
        }

        sendNewMessage(telegramClient, update, screen);
    }

    private boolean canEdit(Update update) {
        return update.getCallbackQuery() != null
                && update.getCallbackQuery().getMessage() != null
                && update.getCallbackQuery().getMessage().getMessageId() != null
                && update.getCallbackQuery().getMessage().getChatId() != null;
    }

    private void editExistingMessage(TelegramClient telegramClient, Update update, BotScreen screen) throws Exception {
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        int messageId = update.getCallbackQuery().getMessage().getMessageId();

        BotImage image = screen.image();
        if (image != null) {
            InputMediaPhoto mediaPhoto = new InputMediaPhoto(
                    new ByteArrayInputStream(image.bytes()),
                    image.fileName()
            );
            mediaPhoto.setCaption(screen.captionHtml() == null ? "" : screen.captionHtml());
            mediaPhoto.setParseMode(HTML);
            EditMessageMedia editMessageMedia = EditMessageMedia.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .media(mediaPhoto)
                    .replyMarkup(screen.keyboard())
                    .build();
            telegramClient.execute(editMessageMedia);
            return;
        }

        EditMessageText editMessageText = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(screen.textHtml() == null ? "" : screen.textHtml())
                .parseMode(HTML)
                .disableWebPagePreview(true)
                .replyMarkup(screen.keyboard())
                .build();
        telegramClient.execute(editMessageText);
    }

    private void sendNewMessage(TelegramClient telegramClient, Update update, BotScreen screen) {
        Message directMessage = update.getMessage();
        Long chatId = directMessage == null ? null : directMessage.getChatId();
        if (chatId == null && update.getCallbackQuery() != null) {
            MaybeInaccessibleMessage callbackMessage = update.getCallbackQuery().getMessage();
            chatId = callbackMessage == null ? null : callbackMessage.getChatId();
        }
        if (chatId == null) {
            return;
        }
        BotImage image = screen.image();
        try {
            if (image != null) {
                SendPhoto sendPhoto = SendPhoto.builder()
                        .chatId(chatId)
                        .photo(new InputFile(new ByteArrayInputStream(image.bytes()), image.fileName()))
                        .caption(screen.captionHtml() == null ? "" : screen.captionHtml())
                        .parseMode(HTML)
                        .replyMarkup(screen.keyboard())
                        .build();
                telegramClient.execute(sendPhoto);
                return;
            }

            SendMessage sendMessage = SendMessage.builder()
                    .chatId(chatId)
                    .text(screen.textHtml() == null ? "" : screen.textHtml())
                    .parseMode(HTML)
                    .disableWebPagePreview(true)
                    .replyMarkup(screen.keyboard())
                    .build();
            telegramClient.execute(sendMessage);
        } catch (Exception exception) {
            log.warn("Failed to send Telegram message", exception);
        }
    }

    private void answerCallbackSilently(TelegramClient telegramClient, String callbackQueryId) {
        try {
            telegramClient.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackQueryId).build());
        } catch (Exception exception) {
            log.debug("Failed to answer callback query");
        }
    }
}
