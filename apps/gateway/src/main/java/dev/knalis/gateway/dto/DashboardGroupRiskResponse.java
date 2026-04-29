package dev.knalis.gateway.dto;

import java.util.UUID;

public record DashboardGroupRiskResponse(
        UUID groupId,
        String riskLevel,
        long affectedStudentsCount
) {
}
