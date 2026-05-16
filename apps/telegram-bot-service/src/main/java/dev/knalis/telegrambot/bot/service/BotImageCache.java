package dev.knalis.telegrambot.bot.service;

import dev.knalis.telegrambot.bot.model.BotImage;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BotImageCache {

    private static final Duration TTL = Duration.ofMinutes(15);
    private final Map<String, CacheEntry> images = new ConcurrentHashMap<>();

    public BotImage get(String key) {
        CacheEntry entry = images.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            images.remove(key);
            return null;
        }
        return entry.image();
    }

    public void put(String key, BotImage image) {
        images.put(key, new CacheEntry(image, Instant.now().plus(TTL)));
    }

    private record CacheEntry(
            BotImage image,
            Instant expiresAt
    ) {
    }
}
