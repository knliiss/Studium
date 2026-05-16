package dev.knalis.notification.service;

import dev.knalis.notification.config.TelegramProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBotApiClient {

    private final TelegramProperties telegramProperties;

    public void sendMessage(long chatId, String text) {
        String botToken = telegramProperties.getBotToken();
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalStateException("TELEGRAM_BOT_TOKEN is missing");
        }

        String endpoint = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        RestClient client = RestClient.create();

        try {
            client.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "chat_id", chatId,
                            "text", text,
                            "disable_web_page_preview", true
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            log.warn("Telegram sendMessage failed for chatId={}", chatId, exception);
            throw exception;
        }
    }
}
