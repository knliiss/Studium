package dev.knalis.telegrambot.bot.service;

import dev.knalis.telegrambot.config.TelegramBotProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class BotLinkFactory {

    private final TelegramBotProperties telegramBotProperties;
    private final TelegramUrlPolicy telegramUrlPolicy;
    private final Set<String> skippedLogKeys = ConcurrentHashMap.newKeySet();

    public LinkDecision resolve(String path) {
        String url = absoluteUrl(path);
        TelegramUrlPolicy.UrlDecision urlDecision = telegramUrlPolicy.evaluate(url);
        if (!urlDecision.allowed()) {
            String logKey = path + "|" + urlDecision.reason();
            if (skippedLogKeys.add(logKey)) {
                log.debug("Skipped Telegram URL button for path={} reason={}", path, urlDecision.reason());
            }
            return new LinkDecision(false, url, path, urlDecision.reason());
        }
        return new LinkDecision(true, url, path, "ok");
    }

    public boolean hasPublicFrontendBaseUrl() {
        return resolve("/profile").allowed();
    }

    private String absoluteUrl(String path) {
        String normalizedPath = path == null || path.isBlank() ? "/" : path.trim();
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }

        String baseUrl = telegramBotProperties.getFrontendBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://localhost:3000" + normalizedPath;
        }
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBase + normalizedPath;
    }

    public record LinkDecision(
            boolean allowed,
            String url,
            String path,
            String reason
    ) {
    }
}
