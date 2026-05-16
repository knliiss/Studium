package dev.knalis.telegrambot.bot.service;

import org.springframework.stereotype.Component;

@Component
public class BotHtmlEscaper {

    public String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
