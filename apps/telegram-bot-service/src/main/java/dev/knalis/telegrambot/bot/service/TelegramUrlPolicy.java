package dev.knalis.telegrambot.bot.service;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

@Component
public class TelegramUrlPolicy {

    private static final Set<String> DISALLOWED_HOSTS = Set.of(
            "localhost",
            "127.0.0.1",
            "0.0.0.0",
            "host.docker.internal"
    );

    public UrlDecision evaluate(String url) {
        if (url == null || url.isBlank()) {
            return new UrlDecision(false, "blank-url");
        }

        final URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException exception) {
            return new UrlDecision(false, "malformed-url");
        }

        String scheme = normalize(uri.getScheme());
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            return new UrlDecision(false, "unsupported-scheme");
        }

        String host = normalize(uri.getHost());
        if (host.isBlank()) {
            return new UrlDecision(false, "missing-host");
        }

        if (DISALLOWED_HOSTS.contains(host)) {
            return new UrlDecision(false, "localhost-or-loopback");
        }

        if (isPrivateIpv4(host)) {
            return new UrlDecision(false, "private-ipv4");
        }

        if (host.indexOf('.') < 0) {
            return new UrlDecision(false, "internal-hostname");
        }

        if (host.endsWith(".local")
                || host.endsWith(".internal")
                || host.endsWith(".localhost")
                || host.endsWith(".lan")
                || host.endsWith(".home")
                || host.endsWith(".docker")) {
            return new UrlDecision(false, "internal-domain");
        }

        return new UrlDecision(true, "ok");
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isPrivateIpv4(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        int[] numbers = new int[4];
        for (int index = 0; index < 4; index++) {
            try {
                numbers[index] = Integer.parseInt(parts[index]);
            } catch (NumberFormatException exception) {
                return false;
            }
            if (numbers[index] < 0 || numbers[index] > 255) {
                return false;
            }
        }

        return numbers[0] == 10
                || (numbers[0] == 172 && numbers[1] >= 16 && numbers[1] <= 31)
                || (numbers[0] == 192 && numbers[1] == 168)
                || (numbers[0] == 169 && numbers[1] == 254);
    }

    public record UrlDecision(
            boolean allowed,
            String reason
    ) {
    }
}
