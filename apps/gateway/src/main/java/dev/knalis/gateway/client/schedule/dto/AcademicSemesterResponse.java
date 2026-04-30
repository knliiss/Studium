package dev.knalis.gateway.client.schedule.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AcademicSemesterResponse(
        UUID id,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate weekOneStartDate,
        boolean active,
        boolean published,
        Instant createdAt,
        Instant updatedAt
) {
}
