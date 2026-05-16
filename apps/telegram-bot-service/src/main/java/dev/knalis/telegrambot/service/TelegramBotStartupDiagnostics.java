package dev.knalis.telegrambot.service;

import dev.knalis.telegrambot.config.TelegramBotProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBotStartupDiagnostics implements ApplicationRunner {

    private final TelegramBotProperties telegramBotProperties;

    @Override
    public void run(ApplicationArguments args) {
        TelegramBotRuntimeState runtimeState = TelegramBotRuntimeInspector.inspect(telegramBotProperties);
        log.info("Telegram startup diagnostics: TELEGRAM_ENABLED={}", runtimeState.telegramEnabled());
        log.info("Telegram startup diagnostics: botUsernameConfigured={}", runtimeState.botUsernameConfigured());
        log.info("Telegram startup diagnostics: tokenPresent={}", runtimeState.tokenPresent());
        log.info("Telegram startup diagnostics: tokenValidationStatus={}", runtimeState.tokenValidationStatus());
        log.info("Telegram startup diagnostics: pollingEnabled={}", runtimeState.pollingEnabled());
        log.info(
                "Telegram startup diagnostics: notificationServiceUrlConfigured={}",
                runtimeState.notificationServiceUrlConfigured()
        );
        log.info("Telegram startup diagnostics: internalSecretPresent={}", runtimeState.internalSecretPresent());
        if (!runtimeState.pollingEnabled()) {
            log.warn("Telegram long polling is disabled by startup validation");
        }
    }
}
