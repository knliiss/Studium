package dev.knalis.schedule.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateAcademicSemesterRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        @NotNull
        LocalDate startDate,

        @NotNull
        LocalDate endDate,

        @NotNull
        LocalDate weekOneStartDate,

        @NotNull
        @Positive
        Integer semesterNumber,

        boolean active,

        boolean published
) {
}
