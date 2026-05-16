package dev.knalis.telegrambot.dto;

public record InternalTelegramScheduleLessonItem(
        String time,
        String subject,
        String lessonType,
        String location,
        String counterpart
) {
}
