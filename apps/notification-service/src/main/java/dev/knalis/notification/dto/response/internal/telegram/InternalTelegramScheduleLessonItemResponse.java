package dev.knalis.notification.dto.response.internal.telegram;

public record InternalTelegramScheduleLessonItemResponse(
        String time,
        String subject,
        String lessonType,
        String location,
        String counterpart
) {
}
