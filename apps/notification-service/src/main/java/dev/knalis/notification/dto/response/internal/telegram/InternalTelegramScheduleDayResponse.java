package dev.knalis.notification.dto.response.internal.telegram;

import java.time.LocalDate;
import java.util.List;

public record InternalTelegramScheduleDayResponse(
        LocalDate date,
        boolean available,
        List<InternalTelegramScheduleLessonItemResponse> lessons
) {
}
