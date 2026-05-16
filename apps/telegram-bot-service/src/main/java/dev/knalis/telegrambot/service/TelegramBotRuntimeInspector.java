package dev.knalis.telegrambot.service;

import dev.knalis.telegrambot.config.TelegramBotProperties;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.regex.Pattern;

public final class TelegramBotRuntimeInspector {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[0-9]{8,12}:[A-Za-z0-9_-]{20,}$");
    private static final Object CACHE_LOCK = new Object();
    private static volatile String cachedKey;
    private static volatile TelegramBotRuntimeState cachedState;

    private TelegramBotRuntimeInspector() {
    }

    public static TelegramBotRuntimeState inspect(Environment environment) {
        TelegramBotProperties properties = new TelegramBotProperties();
        properties.setEnabled(Boolean.parseBoolean(environment.getProperty("app.telegram-bot.enabled", "false")));
        properties.setBotToken(environment.getProperty("app.telegram-bot.bot-token"));
        properties.setBotUsername(environment.getProperty("app.telegram-bot.bot-username"));
        properties.setNotificationServiceBaseUrl(environment.getProperty("app.telegram-bot.notification-service-base-url"));
        properties.setInternalSecret(environment.getProperty("app.telegram-bot.internal-secret"));
        return inspect(properties);
    }

    public static TelegramBotRuntimeState inspect(TelegramBotProperties properties) {
        String key = buildKey(properties);
        TelegramBotRuntimeState localCachedState = cachedState;
        if (key.equals(cachedKey) && localCachedState != null) {
            return localCachedState;
        }

        synchronized (CACHE_LOCK) {
            if (key.equals(cachedKey) && cachedState != null) {
                return cachedState;
            }

            TelegramBotRuntimeState computed = computeState(properties);
            cachedKey = key;
            cachedState = computed;
            return computed;
        }
    }

    private static TelegramBotRuntimeState computeState(TelegramBotProperties properties) {
        boolean telegramEnabled = properties.isEnabled();
        boolean botUsernameConfigured = hasText(properties.getBotUsername());
        boolean tokenPresent = hasText(properties.getBotToken());
        boolean notificationServiceUrlConfigured = hasText(properties.getNotificationServiceBaseUrl());
        boolean internalSecretPresent = hasText(properties.getInternalSecret());

        TelegramTokenValidationStatus tokenValidationStatus = validateToken(telegramEnabled, properties.getBotToken());
        boolean pollingEnabled = telegramEnabled && tokenValidationStatus == TelegramTokenValidationStatus.VALID;

        return new TelegramBotRuntimeState(
                telegramEnabled,
                botUsernameConfigured,
                tokenPresent,
                tokenValidationStatus,
                pollingEnabled,
                notificationServiceUrlConfigured,
                internalSecretPresent
        );
    }

    private static TelegramTokenValidationStatus validateToken(boolean telegramEnabled, String token) {
        if (!telegramEnabled) {
            return TelegramTokenValidationStatus.DISABLED;
        }
        if (!hasText(token)) {
            return TelegramTokenValidationStatus.TOKEN_MISSING;
        }

        String normalizedToken = token.trim();
        String loweredToken = normalizedToken.toLowerCase();
        if (loweredToken.contains("change-me")
                || loweredToken.contains("your-")
                || loweredToken.contains("placeholder")
                || loweredToken.contains("example")
                || loweredToken.endsWith("-token")) {
            return TelegramTokenValidationStatus.TOKEN_PLACEHOLDER;
        }

        if (!TOKEN_PATTERN.matcher(normalizedToken).matches()) {
            return TelegramTokenValidationStatus.TOKEN_FORMAT_INVALID;
        }

        try {
            TelegramGetMeResponse response = RestClient.create("https://api.telegram.org")
                    .get()
                    .uri("/bot{token}/getMe", normalizedToken)
                    .retrieve()
                    .body(TelegramGetMeResponse.class);

            if (response == null || !Boolean.TRUE.equals(response.ok())) {
                return TelegramTokenValidationStatus.TOKEN_API_ERROR;
            }
            return TelegramTokenValidationStatus.VALID;
        } catch (RestClientResponseException exception) {
            int statusCode = exception.getStatusCode().value();
            if (statusCode == 401) {
                return TelegramTokenValidationStatus.TOKEN_UNAUTHORIZED;
            }
            if (statusCode == 404) {
                return TelegramTokenValidationStatus.TOKEN_NOT_FOUND;
            }
            return TelegramTokenValidationStatus.TOKEN_API_ERROR;
        } catch (RestClientException exception) {
            return TelegramTokenValidationStatus.TOKEN_API_ERROR;
        }
    }

    private static String buildKey(TelegramBotProperties properties) {
        return properties.isEnabled()
                + "|"
                + nullSafe(properties.getBotToken())
                + "|"
                + nullSafe(properties.getBotUsername())
                + "|"
                + nullSafe(properties.getNotificationServiceBaseUrl())
                + "|"
                + nullSafe(properties.getInternalSecret());
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record TelegramGetMeResponse(Boolean ok) {
    }
}
