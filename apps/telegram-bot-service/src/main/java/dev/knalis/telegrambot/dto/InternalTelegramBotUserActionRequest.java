package dev.knalis.telegrambot.dto;

import java.util.UUID;

public record InternalTelegramBotUserActionRequest(
        UUID linkId
) {
}
