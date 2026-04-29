package dev.knalis.education.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateSubjectRequest(
        
        @NotBlank
        @Size(max = 100)
        String name,

        UUID groupId,

        @Size(max = 50)
        List<UUID> groupIds,

        @Size(max = 50)
        List<UUID> teacherIds,
        
        @Size(max = 1000)
        String description
) {
}
