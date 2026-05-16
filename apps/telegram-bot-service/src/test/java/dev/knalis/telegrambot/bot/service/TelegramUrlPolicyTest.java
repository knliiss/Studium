package dev.knalis.telegrambot.bot.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramUrlPolicyTest {

    private final TelegramUrlPolicy telegramUrlPolicy = new TelegramUrlPolicy();

    @Test
    void shouldRejectLocalhostUrl() {
        assertFalse(telegramUrlPolicy.evaluate("http://localhost:3000/profile").allowed());
    }

    @Test
    void shouldRejectLoopbackIpUrl() {
        assertFalse(telegramUrlPolicy.evaluate("http://127.0.0.1:3000/profile").allowed());
    }

    @Test
    void shouldRejectRelativeUrl() {
        assertFalse(telegramUrlPolicy.evaluate("/profile").allowed());
    }

    @Test
    void shouldAcceptPublicHttpsUrl() {
        assertTrue(telegramUrlPolicy.evaluate("https://studium.example.com/profile").allowed());
    }
}
