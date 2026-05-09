package dev.knalis.schedule.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AcademicSemesterResponse(
        UUID id,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate weekOneStartDate,
        Integer semesterNumber,
        boolean active,
        boolean published,
        Instant createdAt,
        Instant updatedAt
) {
}
