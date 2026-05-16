package dev.knalis.telegrambot.dto;

import java.time.LocalDate;
import java.util.List;

public record InternalTelegramScheduleDayResponse(
        LocalDate date,
        boolean available,
        List<InternalTelegramScheduleLessonItem> lessons
) {
}
