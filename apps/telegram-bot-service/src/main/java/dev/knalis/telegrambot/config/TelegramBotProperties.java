package dev.knalis.telegrambot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.telegram-bot")
public class TelegramBotProperties {

    private boolean enabled = false;

    private String botToken;

    private String botUsername;

    private String notificationServiceBaseUrl;

    private String internalSecret;

    private String frontendBaseUrl;

    private String ownerTelegramUserIds;

    private String adminTelegramUserIds;
}
