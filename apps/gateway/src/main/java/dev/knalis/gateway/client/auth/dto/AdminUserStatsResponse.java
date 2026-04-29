package dev.knalis.gateway.client.auth.dto;

public record AdminUserStatsResponse(
        long totalUsers,
        long totalEnabledUsers,
        long totalBannedUsers,
        long totalOwners,
        long totalAdmins,
        long totalTeachers,
        long totalStudents,
        long totalRegularUsers
) {
}
