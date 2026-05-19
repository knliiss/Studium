package dev.knalis.gateway.dto.gradebook;

public record GradebookMatrixSummaryResponse(
        int studentCount,
        Double groupAveragePercent,
        Double highestPercent,
        Double lowestPercent,
        int missingCount,
        int pendingCount
) {
}
