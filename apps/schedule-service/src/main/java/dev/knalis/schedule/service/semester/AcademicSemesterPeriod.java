package dev.knalis.schedule.service.semester;

import java.time.LocalDate;

public record AcademicSemesterPeriod(
        String name,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate weekOneStartDate
) {
}
