package dev.knalis.telegrambot.bot.service;

import dev.knalis.telegrambot.bot.model.BotImage;
import dev.knalis.telegrambot.bot.model.BotLocale;
import dev.knalis.telegrambot.dto.InternalTelegramScheduleDayResponse;

import java.time.LocalDate;

public interface BotImageRenderer {

    BotImage renderMainMenuBanner(long telegramUserId, BotLocale locale);

    BotImage renderScheduleDay(long telegramUserId, BotLocale locale, LocalDate date, InternalTelegramScheduleDayResponse scheduleDay);
}
